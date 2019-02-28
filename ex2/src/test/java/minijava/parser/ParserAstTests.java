package minijava.parser;

import frontend.AstPrinter;
import frontend.MJFrontend;
import minijava.ast.*;
import org.hamcrest.CoreMatchers;
import org.junit.Assert;
import org.junit.Test;

import static org.junit.Assert.assertFalse;

public class ParserAstTests {

    @Test
    public void testMinimalProg() throws Exception {
        String input = "class Main { public static void main(String[] args) { }}";
        parse(input);
    }

    @Test
    public void testPrint() throws Exception {
        String input = "class Main { public static void main(String[] args) { System.out.println(42); }}";
        MJProgram ast = parse(input);
        String printed = AstPrinter.print(ast);
        Assert.assertThat(printed, CoreMatchers.containsString("System.out.println(42);"));
    }

    @Test
    public void testLocalVar() throws Exception {
        String input = "class Main { public static void main(String[] args) { boolean x; }}";
        MJProgram ast = parse(input);
        String printed = AstPrinter.print(ast);
        Assert.assertThat(printed, CoreMatchers.containsString("boolean x;"));
    }

    @Test
    public void testAssignment() throws Exception {
        String input = "class Main { public static void main(String[] args) { int x; x = 5; }}";
        MJProgram ast = parse(input);
        String printed = AstPrinter.print(ast);
        Assert.assertThat(printed, CoreMatchers.containsString("x = 5;"));
    }

    @Test
    public void testIfStmt() throws Exception {
        String input = "class Main { public static void main(String[] args) { int x; if (true) x=5; else x = 7;  }}";
        MJProgram ast = parse(input);
        String printed = AstPrinter.print(ast);
        Assert.assertThat(printed, CoreMatchers.containsString("if (true) x = 5;"));
        Assert.assertThat(printed, CoreMatchers.containsString("else x = 7;"));
    }

    @Test
    public void testWhileStmt() throws Exception {
        String input = "class Main { public static void main(String[] args) { int x; while (x < 10) x=x+1;  }}";
        MJProgram ast = parse(input);
        String printed = AstPrinter.print(ast);
        Assert.assertThat(printed, CoreMatchers.containsString("while ((x < 10)) x = (x + 1);"));
    }

    @Test
    public void operators() throws Exception {
        String input = "class Main { public static void main(String[] args) { boolean x; x = ((((3 * 4) + 5) < 2) && (1 < 3)); }}";
        MJProgram ast = parse(input);
        String printed = AstPrinter.print(ast);
        Assert.assertThat(printed, CoreMatchers.containsString("x = ((((3 * 4) + 5) < 2) && (1 < 3))"));
    }

    @Test
    public void operatorPrecedence() throws Exception {
        String input = "class Main { public static void main(String[] args) { boolean x; x = 3*4+5 < 2 && 1 < 3; }}";
        MJProgram ast = parse(input);
        String printed = AstPrinter.print(ast);
        Assert.assertThat(printed, CoreMatchers.containsString("x = ((((3 * 4) + 5) < 2) && (1 < 3))"));
    }

    @Test
    public void operatorAssociativity() throws Exception {
        String input = "class Main { public static void main(String[] args) { boolean x; x = 10 - 5 -3; }}";
        MJProgram ast = parse(input);
        String printed = AstPrinter.print(ast);
        Assert.assertThat(printed, CoreMatchers.containsString("x = ((10 - 5) - 3)"));
    }

    @Test
    public void operatorPrecedenceMethodCallAndArrays() throws Exception {
        String input = "class Main { public static void main(String[] args) { boolean x; x = bar[1].foo; }}";
        MJProgram ast = parse(input);
        String printed = AstPrinter.print(ast);
        Assert.assertThat(printed, CoreMatchers.containsString("x = bar[1].foo"));
    }

    @Test
    public void operatorPrecedenceUnaryAndFields() throws Exception {
        String input = "class Main { public static void main(String[] args) { int x; x = - bar.foo(); }}";
        MJProgram ast = parse(input);
        ast.accept(new MJElement.DefaultVisitor() {
            @Override
            public void visit(MJMethodCall node) {
                Assert.assertEquals("bar.foo()", AstPrinter.print(node));
            }
        });
    }

    @Test
    public void newObjectStatement() throws Exception {
        String input = "class Main { public static void main(String[] args) { new C(); }}";
        MJProgram ast = parse(input);
        String printed = AstPrinter.print(ast);
        Assert.assertThat(printed, CoreMatchers.containsString("new C();"));
    }

    @Test
    public void operatorPrecedenceUnary() throws Exception {
        String input = "class Main { public static void main(String[] args) { boolean x; x = - - ! - ! - 5; }}";
        MJProgram ast = parse(input);
        String printed = AstPrinter.print(ast);
        Assert.assertThat(printed, CoreMatchers.containsString("x = (- (- (! (- (! (- 5))))))"));
    }

    @Test
    public void arrayLength() throws Exception {
        String input = "class Main { public static void main(String[] args) { int x; x = new int[5].length; }}";
        MJProgram ast = parse(input);
        String printed = AstPrinter.print(ast);
        Assert.assertThat(printed, CoreMatchers.containsString("x = (new int[5]).length"));
        ;
    }

    @Test
    public void arrayAccess() throws Exception {
        String input = "class Main { public static void main(String[] args) { int x; x = (new int[5][2]); }}";
        // String input = "class Main { public static void main(String[] args) { int
        // x;}}";
        MJFrontend frontend = new MJFrontend();
        MJProgram ast = frontend.parseString(input);
        if (frontend.getSyntaxErrors().isEmpty()) {
            Assert.fail("should fail: " + AstPrinter.print(ast));
        }
    }

    @Test
    public void testMultipleClasses() throws Exception {
        String input = "class Main{ public static void main(String[] a){}} class A{} class B{} class C{}";
        MJProgram ast = parse(input);
        String printed = AstPrinter.print(ast);
        Assert.assertThat(printed, CoreMatchers.containsString("class A"));
        Assert.assertThat(printed, CoreMatchers.containsString("class B"));
        Assert.assertThat(printed, CoreMatchers.containsString("class C"));
    }

    @Test
    public void testMultipleParameters() throws Exception {
        String input = "class Main{ public static void main(String[] a){}} class A{ int m(int a, boolean b, int c){return 0;}}";
        MJProgram ast = parse(input);
        String printed = AstPrinter.print(ast);
        Assert.assertThat(printed, CoreMatchers.containsString("int m(int a, boolean b, int c)"));
    }

    @Test
    public void testMultipleArgumetns() throws Exception {
        String input = "class Main{public static void main(String[] a){x=a.s(1,2,f+g);}}";
        MJProgram ast = parse(input);
        String printed = AstPrinter.print(ast);
        // System.out.println(printed);
        Assert.assertThat(printed, CoreMatchers.containsString("x = a.s(1, 2, (f + g));"));
    }

    @Test
    public void testMultiarray() throws Exception {
        String input = "class Main { public static void main(String[] args) { int[][] ar; ar = new int[20][]; }}";
        MJProgram ast = parse(input);
        String printed = AstPrinter.print(ast);
        Assert.assertThat(printed, CoreMatchers.containsString("new int[][20]"));
    }

    @Test
    public void testMultiarray2() throws Exception {
        String input = "class Main { public static void main(String[] args) { int[][][] ar; ar = new int[20][][]; }}";
        MJProgram ast = parse(input);
        String printed = AstPrinter.print(ast);
        Assert.assertThat(printed, CoreMatchers.containsString("new int[][][20]"));

        ast.accept(new MJElement.DefaultVisitor() {

            @Override
            public void visit(MJVarDecl v) {
                if (v.getName().equals("ar")) {
                    Assert.assertThat("Type of ar must be an array type", v.getType(),
                            CoreMatchers.instanceOf(MJTypeArray.class));
                }
            }

            @Override
            public void visit(MJNewArray newArray) {
                Assert.assertThat("Basetype of new-array expression must be an array type", newArray.getBaseType(),
                        CoreMatchers.instanceOf(MJTypeArray.class));
            }
        });

    }

    @Test
    public void testArrayLength() throws Exception {
        String input = "class Main { public static void main(String[] args) { int[] ar; System.out.println(ar.length); }}";
        MJProgram ast = parse(input);
        String printed = AstPrinter.print(ast);
        Assert.assertThat(printed, CoreMatchers.containsString("ar.length"));

        boolean[] hasArrayLengthNode = { false };

        ast.accept(new MJElement.DefaultVisitor() {

            @Override
            public void visit(MJArrayLength e) {
                hasArrayLengthNode[0] = true;
            }

        });

        Assert.assertTrue("Program " + printed + " must have an MJArrayLength node", hasArrayLengthNode[0]);
    }

    @Test
    public void testUnaryMinus1() throws Exception {
        String input = "class Main { public static void main(String[] args) { System.out.println(-5 - 4); }}";
        MJProgram ast = parse(input);
        String printed = AstPrinter.print(ast);
        Assert.assertThat(printed, CoreMatchers.containsString("((- 5) - 4)"));
    }

    @Test
    public void testUnaryMinus2() throws Exception {
        String input = "class Main { public static void main(String[] args) { System.out.println(-5 < 4); }}";
        MJProgram ast = parse(input);
        String printed = AstPrinter.print(ast);
        Assert.assertThat(printed, CoreMatchers.containsString("((- 5) < 4)"));
    }

    @Test
    public void testUnaryMinus3() throws Exception {
        String input = "class Main { public static void main(String[] args) { int x; x = -1073741824*2; System.out.println(-x/-2); }}";
        MJProgram ast = parse(input);
        String printed = AstPrinter.print(ast);
        Assert.assertThat(printed, CoreMatchers.containsString("((- x) / (- 2))"));
    }

    @Test
    public void testPrecedenceEqLess() throws Exception {
        String input = "class Main { public static void main(String[] args) { boolean x; x = 1 < 2 == 3 < 4; }}";
        MJProgram ast = parse(input);
        String printed = AstPrinter.print(ast);
        Assert.assertThat(printed, CoreMatchers.containsString("x = ((1 < 2) == (3 < 4))"));
    }

    @Test
    public void testPrecedenceEqAnd() throws Exception {
        String input = "class Main { public static void main(String[] args) { boolean x; x = 1 == 1 && 2 == 2; }}";
        MJProgram ast = parse(input);
        String printed = AstPrinter.print(ast);
        Assert.assertThat(printed, CoreMatchers.containsString("x = ((1 == 1) && (2 == 2))"));
    }

    @Test
    public void testPrecedenceNeg1() throws Exception {
        String input = "class Main { public static void main(String[] args) { boolean x; x = !a && b; }}";
        MJProgram ast = parse(input);
        String printed = AstPrinter.print(ast);
        Assert.assertThat(printed, CoreMatchers.containsString("((! a) && b)"));
    }

    @Test
    public void testPrecedenceNeg2() throws Exception {
        String input = "class Main { public static void main(String[] args) { boolean x; x = !3 < 4; }}";
        MJProgram ast = parse(input);
        String printed = AstPrinter.print(ast);
        Assert.assertThat(printed, CoreMatchers.containsString("((! 3) < 4)"));
    }

    @Test
    public void testStatementsOrder() throws Exception {
        String input = "class Main { public static void main(String[] args) { boolean x; x = 1; x = 2; x = 3; }}";
        MJProgram ast = parse(input);
        String printed = AstPrinter.print(ast);
        Assert.assertThat(printed.replaceAll("\\s+", ""), CoreMatchers.containsString("x=1;x=2;x=3;"));
    }

    @Test
    public void testMethodCallAsStatement() throws Exception {
        String input = "class Main{public static void main(String[] a){a.s(1,2,f+g);}}";
        MJProgram ast = parse(input);
        String printed = AstPrinter.print(ast);
        Assert.assertThat(printed, CoreMatchers.containsString("a.s(1, 2, (f + g));"));
    }

    @Test
    public void testArrayAccessPlus() throws Exception {
        String input = "class Main{public static void main(String[] a){ System.out.println(x + a[y]); }}";
        MJProgram ast = parse(input);
        String printed = AstPrinter.print(ast);
        Assert.assertThat(printed, CoreMatchers.containsString("(x + a[y])"));
    }

    @Test
    public void testArrayAccessMinus() throws Exception {
        String input = "class Main{public static void main(String[] a){ System.out.println(-a[y]); }}";
        MJProgram ast = parse(input);
        String printed = AstPrinter.print(ast);
        Assert.assertThat(printed, CoreMatchers.containsString("(- a[y])"));
    }

    @Test
    public void testArrayAccessNeg() throws Exception {
        String input = "class Main{public static void main(String[] a){ System.out.println(!a[y]); }}";
        MJProgram ast = parse(input);
        String printed = AstPrinter.print(ast);
        Assert.assertThat(printed, CoreMatchers.containsString("(! a[y])"));
    }

    @Test
    public void testArrayAccessUnaryMinus() throws Exception {
        String input = "class Main{public static void main(String[] a){ System.out.println(-a[y]); }}";
        MJProgram ast = parse(input);
        String printed = AstPrinter.print(ast);
        Assert.assertThat(printed, CoreMatchers.containsString("(- a[y])"));
    }

    @Test(expected = frontend.SyntaxError.class)
    public void methodCallAdditionalComma() throws Exception {
        String input = "class Main{public static void main(String[] a){ a.m(x, y, ); }}";
        MJProgram ast = parse(input);
    }

    @Test(expected = frontend.SyntaxError.class)
    public void parameterAdditionalComma() throws Exception {
        String input = "class Main{public static void main(String[] a){}}  class C { int m(int x,) { return 5; } }";
        MJProgram ast = parse(input);

    }

    private MJProgram parse(String input) throws Exception {
        MJFrontend frontend = new MJFrontend();
        MJProgram res = frontend.parseString(input);
        if (frontend.getSyntaxErrors().isEmpty()) {
            return res;
        } else {
            throw frontend.getSyntaxErrors().get(0);
        }
    }

}
