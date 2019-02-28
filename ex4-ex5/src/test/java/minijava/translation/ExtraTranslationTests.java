package minijava.translation;

import org.junit.Test;

public class ExtraTranslationTests {
    @Test
    public void testAssign() throws Exception {
        testStatements("int i; i=0;", "System.out.println(i);");
    }

    @Test
    public void testWhile() throws Exception {
        testStatements("int i; i=0;", "while(i<1000000) { int x; x = 1; i = i + 1; }", "System.out.println(i);");
    }

    @Test
    public void testArray() throws Exception {
        testStatements("int[] ar; ar=null;", "System.out.println(ar[1]);");

    }

    @Test
    public void testDiv() throws Exception {
        testStatements("int x; x = 2147483647 + 1;", "int y; y = -1;", "System.out.println(x/y);"

        );

    }

    @Test
    public void minuszero() throws Exception {
        testStatements("System.out.println(-0);"

        );

    }

    @Test
    public void nulla() throws Exception {
        testStatements("int[] a; a = null;"

        );

    }

    @Test
    public void testArr() throws Exception {
        testStatements("int[] a1;", "int[] a2;", "a1 = new int[19];", "a1[0] = 777;", "a2 = a1;", "a2[0] = 888;",
                "System.out.println(a1[0]);",
                "a1[0] = 5;",
                "System.out.println(a1.length);"

        );

    }

    @Test
    public void divZero() throws Exception {
        testStatements("System.out.println(5 / 0);");
    }

    @Test
    public void divOverflow() throws Exception {
        testStatements("System.out.println((" + (Integer.MAX_VALUE) + " + 1) / -1);");
    }

    @Test
    public void exprNull() throws Exception {
        testStatements("int[] ar; ar = null; System.out.println(ar.length);");
    }

    @Test
    public void fields() throws Exception {
        String input = "class Main { public static void main(String[] args) {\n" + "  A a; \n" + "B b;\n"
                + "b = new B();\n" + "b.x = 1;\n" + "b.y = 2;\n" + "a = b;\n" + "a.x = 3;\n" + "a.y = 4;\n"
                + "System.out.println(b.x);\n" + "System.out.println(b.y);\n" + "System.out.println(a.x);\n"
                + "System.out.println(a.y);" + "\n}} " + "class A {\n" + "  int x;\n" + "  int y;\n" + "}\n"
                + "class B extends A {\n" + "  int y;\n" + "}\n";
        TranslationTestHelper.testLLVMTranslation("Test.java", input);
    }

    @Test
    public void allocaLoop() throws Exception {
        testStatements("int i; i = 0;",
                "while (i < 1000000) { int j; j = 0;  while (j < 1000) { int x; x = 5; j = j + 1; } i = i + 1; }");
    }

    @Test
    public void allocaLoop2() throws Exception {
        testStatements("int i; i = 0;",
                "while (i < 10) { int j; j = 0;  while (j < 1) { int x; x = 5; j = j + 1; } i = i + 1; }");
    }

    @Test
    public void returnInIf() throws Exception {
        String input = "class Main { public static void main(String[] args) {\n" + "System.out.println(new A().foo(4));"
                + "\n}}" + "class A {\n" + "int foo(int x) { if (x<5) return 1; else {} return 2; } " + "}";
        TranslationTestHelper.testLLVMTranslation("Test.java", input);
    }

    @Test
    public void returnInWhile() throws Exception {
        String input = "class Main { public static void main(String[] args) {\n" + "System.out.println(new A().foo(4));"
                + "\n}}" + "class A {\n" + "int foo(int x) { while (x<100) { return 1; } return 2; } " + "}";
        TranslationTestHelper.testLLVMTranslation("Test.java", input);
    }

    private void testStatements(String... inputLines) throws Exception {
        String input = "class Main { public static void main(String[] args) {\n" + String.join("\n", inputLines)
                + "\n}}\n";
        TranslationTestHelper.testLLVMTranslation("Test.java", input);
    }

}
