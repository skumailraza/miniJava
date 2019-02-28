package calculator;

import org.junit.Assert;
import org.junit.Test;

/**
 * This tests the basic version of the 
 * It tests some basic expressions without lambdas or lets.
 * 
 * To run this test you have to create a class named explang.interpret.Interpreter
 * This class should have a static method named "run" which takes a String, 
 * parses and evaluates it and returns the result of the evaluation as an int.
 *  
 * */
public class InterpreterTest {

    @Test
    public void testArith1() {
        String expr = "((5*3) + 4)";
        int v = Main.run(expr);
        Assert.assertEquals(19, v);
    }

    @Test
    public void testArith2() {
        String expr = "((5*3) + (60 / (15 - 5)))";
        int v = Main.run(expr);
        Assert.assertEquals(21, v);
    }

    @Test
    public void testIfTrue() {
        String expr = "if (3 == 3) then 4 else 5";
        int v = Main.run(expr);
        Assert.assertEquals(4, v);
    }

    @Test
    public void testIfFalse() {
        String expr = "if (2 == 3) then 4 else 5";
        int v = Main.run(expr);
        Assert.assertEquals(5, v);
    }

    @Test
    public void paren_expr() {
        String expr = "(((((5)))))";
        int v = Main.run(expr);
        Assert.assertEquals(5, v);
    }


}
