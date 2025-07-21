import top.nontage.mcputils.MCPMapping;

public class Test {
    public static void main(String[] args) {
        MCPMapping mcp = new MCPMapping(MCPMapping.Version.V1_8_R3, MCPMapping.Client.VANILLA);
        String className = "net.minecraft.client.Minecraft";
        String obfClassName = mcp.getObfClass(className);
        System.out.println("Original Class Name: " + className);
        System.out.println("Obfuscated Class Name: " + obfClassName);

        String fieldName = "theMinecraft";
        String obfFieldName = mcp.getObfField(className, fieldName);
        System.out.println("Original Field Name: " + fieldName);
        System.out.println("Obfuscated Field Name: " + obfFieldName);

        String methodName = "runTick";
        String obfMethodName = mcp.getObfMethod(className, methodName, "()V");
        System.out.println("Original Method Name: " + methodName);
        System.out.println("Obfuscated Method Name: " + obfMethodName);

    }
}
