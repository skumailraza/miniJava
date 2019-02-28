package calculator;

import exprs.ExprParserSym;
import exprs.Lexer;
import java_cup.runtime.ComplexSymbolFactory;
import java_cup.runtime.Symbol;
import org.junit.Test;

import java.io.IOException;
import java.io.StringReader;

import static org.junit.Assert.assertEquals;

/**
 * This tests whether the lexer correctly splits the input string into tokens.
 * It does not test whether the tokens have the correct class.
 * 
 * This test depends on the generated class explang.lexer.Lexer
 * Make sure the right package "explang" is given in the SableCC grammar file.
 **/
public class LexerTest {

    @Test
    public void simpleTest() throws  IOException {
        String input = "hello + world == compiler";
        assertEquals("ID(hello), PLUS, ID(world), EQUALS, ID(compiler)", printTokens(input));
    }
    
    @Test
    public void simpleTestIf() throws  IOException {
        String input = "if iff";
        assertEquals("IF, ID(iff)", printTokens(input));
    }

    @Test
    public void simpleTest2() throws  IOException {
        String input = "let x = 5 in fun y -> y/x";
        assertEquals("LET, ID(x), EQ, NUMBER(5), IN, FUN, ID(y), ARROW, ID(y), DIV, ID(x)", printTokens(input));
    }


    @Test
    public void lineComment() throws  IOException {
        String input = "3 + 4 -- this is a comment\n == 7";
        assertEquals("NUMBER(3), PLUS, NUMBER(4), EQUALS, NUMBER(7)", printTokens(input));
    }

    @Test
    public void multilineComment() throws  IOException {
        String input = "3 + 4 {- this\n is a\n comment -} == 7";
        assertEquals("NUMBER(3), PLUS, NUMBER(4), EQUALS, NUMBER(7)", printTokens(input));
    }


    private String printTokens(String input) throws  IOException {
        ComplexSymbolFactory sf = new ComplexSymbolFactory();
        Lexer lex = new Lexer(sf, new StringReader(input));
        StringBuilder result = new StringBuilder();
        while (true) {
            Symbol sym = lex.next_token();
            if (sym.sym == ExprParserSym.EOF) {
                return result.toString();
            }
            if (result.length() > 0) {
                result.append(", ");
            }
            result.append(ExprParserSym.terminalNames[sym.sym]);
            if (sym.value != null) {
                result.append("(");
                result.append(sym.value);
                result.append(")");
            }
        }
    }

}
