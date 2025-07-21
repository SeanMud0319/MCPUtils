package top.nontage.mcputils;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;

public class MCPMapping {

    private final Map<String, String> classMap = new HashMap<>();
    private final Map<String, String> obfFieldMap = new HashMap<>();
    private final Map<String, String> friendlyFieldNameMap = new HashMap<>();
    private final Map<String, String> combinedFieldMap = new HashMap<>();

    private final Map<String, String> obfMethodMap = new HashMap<>();
    private final Map<String, String> friendlyMethodNameMap = new HashMap<>();
    private final Map<String, String> combinedMethodMap = new HashMap<>();
    private final Map<String, String> reverseMethodMap = new HashMap<>();
    private final Map<String, String> reverseFieldMap = new HashMap<>();

    public enum Version {
        V1_8_R3("mappings/v1_8_R3/conf/joined.srg",
                "mappings/v1_8_R3/conf/methods.csv",
                "mappings/v1_8_R3/conf/fields.csv");

        private final String srgPath;
        private final String methodsCsvPath;
        private final String fieldsCsvPath;

        Version(String srgPath, String methodsCsvPath, String fieldsCsvPath) {
            this.srgPath = srgPath;
            this.methodsCsvPath = methodsCsvPath;
            this.fieldsCsvPath = fieldsCsvPath;
        }

        public String getSrgPath() {
            return srgPath;
        }

        public String getMethodsCsvPath() {
            return methodsCsvPath;
        }

        public String getFieldsCsvPath() {
            return fieldsCsvPath;
        }
    }
    public enum Client {
        DISABLE,
        VANILLA,
        FORGE
    }

    private final Client client;
    public MCPMapping(Version version, Client client) {
        this.client = client;
        loadJoinedSrg(version.getSrgPath());
        loadMethodsCsv(version.getMethodsCsvPath());
        loadFieldsCsv(version.getFieldsCsvPath());
        buildCombinedMethodMap();
        buildCombinedFieldMap();
    }

    private void loadJoinedSrg(String path) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(getResource(path)))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("CL:")) {
                    String[] parts = line.split(" ");
                    if (parts.length >= 3) {
                        String obf = parts[1].replace('/', '.');
                        String readable = parts[2].replace('/', '.');
                        classMap.put(readable, obf);
                    }
                } else if (line.startsWith("FD:")) {
                    String[] parts = line.split(" ");
                    if (parts.length >= 3) {
                        String obf = parts[1].replace('/', '.');
                        String readable = parts[2].replace('/', '.');
                        obfFieldMap.put(readable, obf);
                    }
                } else if (line.startsWith("MD:")) {
                    String[] parts = line.split(" ");
                    if (parts.length >= 5) {
                        String obf = parts[1].replace('/', '.') + parts[2];
                        String readable = parts[3].replace('/', '.') + parts[4];
                        obfMethodMap.put(readable, obf);
                    }
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to load joined.srg: " + path, e);
        }
    }

    private void loadMethodsCsv(String path) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(getResource(path)))) {
            reader.readLine();
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split(",");
                if (parts.length >= 2) {
                    String searge = parts[0];
                    String friendly = parts[1];
                    friendlyMethodNameMap.put(searge, friendly);
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to load methods.csv: " + path, e);
        }
    }

    private void loadFieldsCsv(String path) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(getResource(path)))) {
            reader.readLine();
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split(",");
                if (parts.length >= 2) {
                    String searge = parts[0];
                    String friendly = parts[1];
                    friendlyFieldNameMap.put(searge, friendly);
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to load fields.csv: " + path, e);
        }
    }

    private void buildCombinedMethodMap() {
        for (Map.Entry<String, String> entry : obfMethodMap.entrySet()) {
            String readableKey = entry.getKey();
            String obfValue = entry.getValue();

            int lastDot = readableKey.lastIndexOf('.');
            String className = readableKey.substring(0, lastDot);
            String funcNameWithDesc = readableKey.substring(lastDot + 1);

            int paramStart = funcNameWithDesc.indexOf('(');
            String funcName = funcNameWithDesc.substring(0, paramStart);
            String descriptor = funcNameWithDesc.substring(paramStart);

            String friendlyName = friendlyMethodNameMap.getOrDefault(funcName, funcName);
            String combinedKey = className + "." + friendlyName + descriptor;

            combinedMethodMap.put(combinedKey, obfValue);
            reverseMethodMap.put(combinedKey, funcName);
        }
    }

    private void buildCombinedFieldMap() {
        for (Map.Entry<String, String> entry : obfFieldMap.entrySet()) {
            String readableKey = entry.getKey();
            String obfValue = entry.getValue();

            int lastDot = readableKey.lastIndexOf('.');
            if (lastDot == -1) continue;

            String className = readableKey.substring(0, lastDot);
            String fieldName = readableKey.substring(lastDot + 1);

            String friendlyName = friendlyFieldNameMap.getOrDefault(fieldName, fieldName);
            String combinedKey = className + "." + friendlyName;

            combinedFieldMap.put(combinedKey, obfValue);
            reverseFieldMap.put(combinedKey, fieldName);
        }
    }

    private InputStream getResource(String path) {
        InputStream stream = getClass().getClassLoader().getResourceAsStream(path);
        if (stream == null) throw new RuntimeException("Resource not found: " + path);
        return stream;
    }

    public String getObfClass(String readableClass) {
        if (client == Client.DISABLE || client == Client.FORGE) return readableClass;
        return classMap.getOrDefault(readableClass, readableClass);
    }

    public String getObfMethod(String readableClass, String readableMethodName, String readableDescriptor) {
        if (client == Client.DISABLE) return readableMethodName;

        String key = readableClass + "." + readableMethodName + readableDescriptor;
        if (client == Client.FORGE) {
            return reverseMethodMap.getOrDefault(key, readableMethodName);
        }

        String fullObf = combinedMethodMap.get(key);
        if (fullObf == null) return readableMethodName;

        int lastDot = fullObf.lastIndexOf('.');
        if (lastDot == -1) return fullObf;
        String methodWithDesc = fullObf.substring(lastDot + 1);
        int parenIndex = methodWithDesc.indexOf('(');
        if (parenIndex == -1) return methodWithDesc;
        return methodWithDesc.substring(0, parenIndex);
    }

    public String getObfField(String readableClass, String readableFieldName) {
        if (client == Client.DISABLE) return readableFieldName;

        String key = readableClass + "." + readableFieldName;
        if (client == Client.FORGE) {
            return reverseFieldMap.getOrDefault(key, readableFieldName);
        }

        String fullObf = combinedFieldMap.get(key);
        if (fullObf == null) return readableFieldName;

        int lastDot = fullObf.lastIndexOf('.');
        if (lastDot == -1) return fullObf;
        return fullObf.substring(lastDot + 1);
    }

    /**
     * Exports the current mappings to a .srg file.
     * The format is:
     * CL: obfClass deobfClass
     * FD: obfClass/obfField deobfClass/deobfField
     * MD: obfClass/obfMethod deobfClass/deobfMethod
     * @param output
     */
    public void exportSrgToFile(File output) {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(output))) {
            for (Map.Entry<String, String> entry : classMap.entrySet()) {
                String deobf = entry.getKey().replace('.', '/');
                String obf = entry.getValue().replace('.', '/');
                writer.write("CL: " + obf + " " + deobf);
                writer.newLine();
            }

            for (Map.Entry<String, String> entry : combinedFieldMap.entrySet()) {
                String key = entry.getKey();
                String obfFull = entry.getValue();

                int lastDotKey = key.lastIndexOf('.');
                if (lastDotKey == -1) continue;

                String deobfClass = key.substring(0, lastDotKey).replace('.', '/');
                String deobfField = key.substring(lastDotKey + 1);

                int lastDotObf = obfFull.lastIndexOf('.');
                if (lastDotObf == -1) continue;

                String obfClass = obfFull.substring(0, lastDotObf).replace('.', '/');
                String obfField = obfFull.substring(lastDotObf + 1);

                writer.write("FD: " + obfClass + "/" + obfField + " " + deobfClass + "/" + deobfField);
                writer.newLine();
            }

            for (Map.Entry<String, String> entry : combinedMethodMap.entrySet()) {
                String key = entry.getKey();
                String obfFull = entry.getValue();

                int lastDotKey = key.lastIndexOf('.');
                if (lastDotKey == -1) continue;

                String deobfClass = key.substring(0, lastDotKey).replace('.', '/');

                String methodNameWithDesc = key.substring(lastDotKey + 1);
                int descStart = methodNameWithDesc.indexOf('(');
                if (descStart == -1) continue;

                String deobfMethod = methodNameWithDesc.substring(0, descStart);
                String deobfDesc = methodNameWithDesc.substring(descStart);

                int lastDotObf = obfFull.lastIndexOf('.');
                if (lastDotObf == -1) continue;

                String obfClass = obfFull.substring(0, lastDotObf).replace('.', '/');
                String obfMethodWithDesc = obfFull.substring(lastDotObf + 1);

                int parenStart = obfMethodWithDesc.indexOf('(');
                if (parenStart == -1) continue;

                String obfMethod = obfMethodWithDesc.substring(0, parenStart);
                String obfDesc = obfMethodWithDesc.substring(parenStart);

                writer.write("MD: " + obfClass + "/" + obfMethod + " " + obfDesc + " " + deobfClass + "/" + deobfMethod + " " + deobfDesc);
                writer.newLine();
            }


            System.out.println("SRG exported to: " + output.getAbsolutePath());

        } catch (IOException e) {
            throw new RuntimeException("Failed to export SRG file", e);
        }
    }
    public void exportForgeMappingToFile(File output) {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(output))) {
            for (Map.Entry<String, String> entry : reverseFieldMap.entrySet()) {
                String friendlyKey = entry.getKey();
                String obfFieldName = entry.getValue();

                int lastDotKey = friendlyKey.lastIndexOf('.');
                if (lastDotKey == -1) continue;

                String friendlyClass = friendlyKey.substring(0, lastDotKey).replace('.', '/');
                String friendlyField = friendlyKey.substring(lastDotKey + 1);

                String obfFull = obfFieldMap.get(friendlyKey);
                if (obfFull == null) obfFull = friendlyClass + "/" + obfFieldName;
                else obfFull = obfFull.replace('.', '/');

                writer.write("FD: " + obfFull + " " + friendlyClass + "/" + friendlyField);
                writer.newLine();
            }

            for (Map.Entry<String, String> entry : reverseMethodMap.entrySet()) {
                String friendlyKey = entry.getKey();
                String obfMethodName = entry.getValue();

                int lastDotKey = friendlyKey.lastIndexOf('.');
                if (lastDotKey == -1) continue;

                String friendlyClass = friendlyKey.substring(0, lastDotKey).replace('.', '/');
                String methodWithDesc = friendlyKey.substring(lastDotKey + 1);

                int descStart = methodWithDesc.indexOf('(');
                if (descStart == -1) continue;

                String friendlyMethod = methodWithDesc.substring(0, descStart);
                String friendlyDesc = methodWithDesc.substring(descStart);

                String obfFull = obfMethodMap.get(friendlyKey);
                if (obfFull == null) {
                    obfFull = friendlyClass + "/" + obfMethodName + " " + friendlyDesc;
                } else {
                    obfFull = obfFull.replace('.', '/');
                }

                int spaceIdx = obfFull.indexOf(' ');
                if (spaceIdx == -1) continue;

                String obfClass = obfFull.substring(0, spaceIdx);
                String obfMethodWithDesc = obfFull.substring(spaceIdx + 1);

                writer.write("MD: " + obfClass + " " + obfMethodWithDesc + " " + friendlyClass + "/" + friendlyMethod + " " + friendlyDesc);
                writer.newLine();
            }

            System.out.println("Forge mapping exported to: " + output.getAbsolutePath());
        } catch (IOException e) {
            throw new RuntimeException("Failed to export Forge mapping file", e);
        }
    }

}

