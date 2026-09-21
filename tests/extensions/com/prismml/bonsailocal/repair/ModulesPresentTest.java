package com.prismml.bonsailocal.repair;
public class ModulesPresentTest {
 public static void main(String[]a){for(String name:new String[]{"WebTools","McpServer","BonsaiChromeClient"})try{Class.forName("com.prismml.bonsailocal.repair."+name,false,ModulesPresentTest.class.getClassLoader());}catch(ClassNotFoundException e){throw new AssertionError("Required local extension missing: "+name,e);}}
}
