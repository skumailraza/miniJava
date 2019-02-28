package calculator;

import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;

/**
 * This is a test for the optional exercise. It tests lambda expressions and let
 * expressions.
 * <p>
 * This test has the same prerequisites as the InterpreterTest
 */
public class LambdaTest {

    /***
     * The y-combinator is a fixpoint operator. It satisfies the equation Y f = f (Y
     * f).
     */
    private static final String yCombinator = "let Y = fun f -> (fun  x -> f (x x)) (fun x -> f (x x)) in \n";
    /***
     * The z-combinator is a fixpoint operator like the y-combinator but it also
     * works with strict evaluation.
     */
    private static final String zCombinator = "let Z = fun f -> (fun x -> f (fun v -> ((x x) v))) (fun x -> f (fun v -> ((x x) v))) in \n";

    @Test
    public void testSimple() {
        String expr = "((fun x -> (x + 1)) 5)";
        int v = Main.run(expr);
        Assert.assertEquals(6, v);
    }

    @Test
    @Ignore
    public void testClosure() {
        String expr = "(((fun x -> (fun y -> (x + y))) 2) 4)";
        int v = Main.run(expr);
        Assert.assertEquals(6, v);
    }

    @Test
    @Ignore
    public void testApplyTwice() {
        String expr = "let applyTwice = (fun f -> (fun x -> (f (f x)))) in ((applyTwice (fun y -> (y*y))) 2)";
        int v = Main.run(expr);
        Assert.assertEquals(16, v);
    }

    @Test
    public void testIf() {
        String expr = "((fun n -> if (n==0) then 1 else 2) 1)";
        int v = Main.run(expr);
        Assert.assertEquals(2, v);
    }

    @Test
    public void testlet1() {
        String expr = "let x = 5 in (x * 3)";
        int v = Main.run(expr);
        Assert.assertEquals(15, v);
    }

    @Test
    public void testlet2() {
        String expr = "let x = 5 in (let y = 3 in (x * y))";
        int v = Main.run(expr);
        Assert.assertEquals(15, v);
    }

    @Test
    public void testlet3() {
        String expr = "let x = (let a = 5 in (a * 1)) in (let y = 3 in (x * y))";
        int v = Main.run(expr);
        Assert.assertEquals(15, v);
    }

    @Test(expected = Exception.class)
    public void testLetScope1() {
        String expr = "((let y = 5 in y + 5) + y)";
        Main.run(expr);
    }

    @Test(expected = Exception.class)
    public void testLetScope2() {
        String expr = "(y + (let y = 5 in y + 5))";
        Main.run(expr);
    }

    @Test
    public void testLetScope3() {
        String expr = "let y = 5 in (y + (let y = 3 in (y * 5)))";
        int v = Main.run(expr);
        Assert.assertEquals(20, v);
    }

    @Test
    @Ignore
    public void testLetScope4() {
        String expr = "let y = 5 in ((let y = 3 in (y * 5)) + y)";
        int v = Main.run(expr);
        Assert.assertEquals(20, v);
    }

    @Test
    public void testLetScope5() {
        String expr = "let x = 5 in ((let y = 3 in (y * x)) + x)";
        int v = Main.run(expr);
        Assert.assertEquals(20, v);
    }

    @Test(expected = Exception.class)
    public void typeError() {
        String expr = "((fun x -> x) + 5)";
        Main.run(expr);
    }

    @Test
    @Ignore
    public void testFac() {
        String expr = "let fac = (fun rec -> (fun n -> " + '\n' + "    if (n == 0) then 1 " + '\n'
                + "    else (n * ((rec rec) (n - 1)))))" + '\n' + "in ((fac fac) 5)";
        int v = Main.run(expr);
        Assert.assertEquals(120, v);
    }

    @Test
    @Ignore
    public void testFib() {
        String expr = "let fib = (fun rec -> " + '\n' + "(fun n -> if (n==0) then 1 " + '\n' + "else if (n==1) then 1 "
                + '\n' + "else (((rec rec) (n-2))+((rec rec) (n-1))))) in " + '\n' + " ((fib fib) 8)";
        int v = Main.run(expr);
        Assert.assertEquals(34, v);
    }

    @Test
    public void testLet() {
        String expr = "let x  = 5 in (x*3)";
        int v = Main.run(expr);
        Assert.assertEquals(15, v);
    }

    @Test
    @Ignore
    public void testLetShadowed() {
        String expr = "let x = 4 in (let x = 3 in 2*x) + x";
        int v = Main.run(expr);
        Assert.assertEquals(10, v);
    }

    @Test
    public void testLetWithIf() {
        String expr = "let y  = 2 in if (y==2) then (y + 3) else (y-1)";
        int v = Main.run(expr);
        Assert.assertEquals(5, v);
    }

    @Test
    @Ignore
    public void testDifferentClosures() {
        String expr = "let f = ((fun x -> (fun y -> (x + y)))) in \n" + "let g = (f 5) in \n" + "let h = (f 4) in \n"
                + "((g 1) + (h 1))";
        int v = Main.run(expr);
        Assert.assertEquals(11, v);
    }

    @Test
    @Ignore // this will loop, unless lazy evaluation is used
    public void yCombinator() {
        String expr = yCombinator + "let facEq = fun f -> fun n -> " + "  if n==0 then 1 " + "  else n * f (n-1) in "
                + "let fac = Z facEq in " + "fac(5)";
        int v = Main.run(expr);
        Assert.assertEquals(120, v);
    }

    @Test
    @Ignore
    public void fac() {
        String expr = zCombinator + "let facEq = fun f -> fun n -> " + "  if n==0 then 1 " + "  else n * f (n-1) in "
                + "let fac = Z facEq in " + "fac(5)";
        int v = Main.run(expr);
        Assert.assertEquals(120, v);
    }

    @Test
    @Ignore
    public void fib() {
        String expr = zCombinator + "let fib = Z (fun fib -> fun n -> " + "  if n<2 then 1 "
                + "  else fib(n-2) + fib(n-1)) in " + "fib(6)";
        int v = Main.run(expr);
        Assert.assertEquals(13, v);
    }

    @Test
    @Ignore
    public void sqrt() {
        String expr = zCombinator + "let sqrt = Z (fun sqrt -> fun n -> " + "  if n < 1 then 0 " + "  else "
                + "    let r = sqrt(n-1) in " + "    if n < (r+1)*(r+1) then r " + "    else r + 1) in " + "sqrt(25)";
        int v = Main.run(expr);
        Assert.assertEquals(5, v);
    }

}
