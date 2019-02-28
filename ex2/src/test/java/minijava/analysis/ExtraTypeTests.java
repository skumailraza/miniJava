package minijava.analysis;

import analysis.Analysis;
import frontend.MJFrontend;
import frontend.SyntaxError;
import minijava.ast.MJProgram;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;

import static junit.framework.TestCase.assertFalse;
import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.fail;

public class ExtraTypeTests {

    @Rule
    public Timeout globalTimeout = Timeout.seconds(2);

    @Test
    public void testNullOk() {
        expectOk(
                "class Main { public static void main(String[] args) { int[] ar; ar = null; } }"
        );
    }

    @Test
    public void testNullErr() {
        expectTypeErrors(
                "class Main { public static void main(String[] args) { int ar; ar = null; } }"
        );
    }


    @Test
    public void testNameMain() {
        expectTypeErrors(
                "class Main { public static void main(String[] args) { } }",
                "class Main {}"
        );
    }

    @Test
    public void subtype() {
        expectOk(
                "class Main { public static void main(String[] args) { A a; a = new B(); } }",
                "class A {}",
                "class B extends A {}"
        );
    }

    @Test
    public void subtype2() {
        expectTypeErrors(
                "class Main { public static void main(String[] args) { B b; b = new A(); } }",
                "class A {}",
                "class B extends A {}"
        );
    }

    @Test
    public void cycle() {
        expectTypeErrors(
                "class Main { public static void main(String[] args) { new B().foo(new C()); } }",
                "class A extends C {",
                "   int foo(D d) { return 1; }",
                "}",
                "class B extends A {}",
                "class C extends B{}",
                "class D {}"
        );
    }

    @Test
    public void cycle3() {
        expectTypeErrors(
                "class Main { public static void main(String[] args) {  }}",
                "class A extends C {}",
                "class B extends A {}",
                "class C extends B{}",
                "class D {}"
        );
    }



    @Test
    public void cycle2() {
        expectTypeErrors(
                "class Main { public static void main(String[] args) { int x; x = new A().foo(new C()); } }",
                "class A extends C {",
                "   int foo(D d) { return 1; }",
                "}",
                "class B extends A {}",
                "class C extends B{}",
                "class D {}"
        );
    }

    @Test
    public void methodInSuper() {
        expectOk(
                "class Main { public static void main(String[] args) { int x; x = new B().foo(); } }",
                "class A {",
                "   int foo() { return 1; }",
                "}",
                "class B extends A {}"
        );
    }


    @Test
    public void methodInSuper2() {
        expectTypeErrors(
                "class Main { public static void main(String[] args) { int x; x = new B().foo(); } }",
                "class A {",
                "   int foo() { return 1; }",
                "}",
                "class B  {}"
        );
    }

    @Test
    public void methodSub() {
        expectOk(
                "class Main { public static void main(String[] args) { new B().foo(new D()); } }",
                "class A {",
                "   int foo(A a) { return 1; }",
                "}",
                "class B extends A {}",
                "class C extends B{}",
                "class D extends C{}"
        );
    }

    @Test
    public void methodSub2() {
        expectTypeErrors(
                "class Main { public static void main(String[] args) { new B().foo(new D()); } }",
                "class A {",
                "   int foo(A a) { return 1; }",
                "}",
                "class B {}",
                "class C extends B{}",
                "class D extends C{}"
        );
    }

    @Test
    public void overrideTransOk() {
        expectOk(
                "class Main { public static void main(String[] args) { } }",
                "class A {",
                "   int foo(int a) { return 1; }",
                "}",
                "class B extends A {}",
                "class C extends B {}",
                "class D extends C{",
                "   int foo(int a) { return 1; }",
                "}"
        );
    }

    @Test
    public void overrideTransFail() {
        expectTypeErrors(
                "class Main { public static void main(String[] args) { } }",
                "class A {",
                "   int foo(int a) { return 1; }",
                "}",
                "class B extends A {}",
                "class C extends B {}",
                "class D extends C{",
                "   boolean foo(int a) { return 1; }",
                "}"
        );
    }


    @Test
    public void overrideTransFail2() {
        expectTypeErrors(
                "class Main { public static void main(String[] args) { } }",
                "class A {",
                "   int foo(A a) { return 1; }",
                "}",
                "class B extends A {}",
                "class C extends B {}",
                "class D extends C{",
                "   int foo(C a) { return 1; }",
                "}"
        );
    }


    @Test
    public void cycleExtra() {
        expectTypeErrors(
                "class Main { public static void main(String[] args) { } }",
                "class A extends B {}",
                "class B extends C {}",
                "class C extends A {}",
                "class D extends C {}"
        );
    }


    @Test
    public void blocksAreCool() {
        expectTypeErrors(
                "class Main { public static void main(String[] args) {{{ System.out.println(true); }}}}"
        );
    }

    @Test
    public void blocksAreCool2() {
        expectOk(
                "class Main { public static void main(String[] args) {{{ System.out.println(42); }}}}"
        );
    }

    @Test
    public void pleaseCheckIfs() {
        expectTypeErrors(
                "class Main { public static void main(String[] args) { if (true) { System.out.println(24); } else { gibtsNicht = hello; }}}"
        );
    }

    @Test
    public void pleaseCheckIfs2() {
        expectTypeErrors(
                "class Main { public static void main(String[] args) { if (true) { System.out.println(true); } else {  }}}"
        );
    }

    @Test
    public void pleaseCheckWhiles() {
        expectTypeErrors(
                "class Main { public static void main(String[] args) { while (true) { System.out.println(true); }}}"
        );
    }


    private void expectTypeErrors(String... inputLines) {
        test(true, inputLines);
    }

    private void expectOk(String... inputLines) {
        test(false, inputLines);
    }

    private void test(boolean expectError, String ... inputLines) {
        try {
            String input = String.join("\n", inputLines);
            MJFrontend frontend = new MJFrontend();
            MJProgram program = frontend.parseString(input);
            if (!frontend.getSyntaxErrors().isEmpty()) {
                SyntaxError syntaxError = frontend.getSyntaxErrors().get(0);
                fail("Unexpected syntax error in line " + syntaxError.getLine() + ")\n" + syntaxError.getMessage());
            }

            Analysis analysis = new Analysis(program);
            analysis.check();


            if (expectError) {
                assertFalse("There should be type errors.", analysis.getTypeErrors().isEmpty());
            } else {
                if (!analysis.getTypeErrors().isEmpty()) {
                    throw analysis.getTypeErrors().get(0);
                }
                assertTrue("There should be no type errors.", analysis.getTypeErrors().isEmpty());
            }
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
