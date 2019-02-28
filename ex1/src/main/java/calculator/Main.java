package calculator;

import calculator.ast.Expr;
import calculator.ast.ExprBinary;
import calculator.ast.ExprNumber;
import calculator.ast.finalvalues.FinalValue;
import calculator.ast.finalvalues.Number;
import exprs.ExprParser;
import exprs.ExprParser.ParserError;
import exprs.Lexer;
import java_cup.runtime.ComplexSymbolFactory;
import java_cup.runtime.Symbol;

import java.io.Reader;
import java.io.StringReader;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;

public class Main {

    public static void main(String[] args) throws Exception {
        /*
         * System.out.println("Write one expression per line:"); Scanner s = new
         * Scanner(System.in); while (s.hasNext()) { try { String input = s.nextLine();
         * Expr e = parseString(input); System.out.println(e.toString()); } catch
         * (ParserError e) { System.out.println(e.toString()); } }
         */

        System.out.println("Write one expression per line:");
        Scanner s = new Scanner(System.in);
        while (s.hasNext()) {
            try {
                String input = s.nextLine();
                Expr expr = parseString(input);
                if (expr != null) {
                    System.out.println(expr + " = " + evaluate(expr));
                }
            } catch (ParserError e) {
                System.out.println(e.toString());
            }
        }

    }

    public static Expr parseString(String input) throws Exception {
        Reader in = new StringReader(input);
        return parse(in);
    }

    public static Expr parse(Reader in) throws Exception {
        ComplexSymbolFactory sf = new ComplexSymbolFactory();
        Lexer lexer = new Lexer(sf, in);
        ExprParser parser = new ExprParser(lexer, sf);

        parser.onError((ParserError e) -> {
            throw e;
        });

        Symbol result = parser.parse();

        return (Expr) result.value;
    }

    static int run(String expr) {
        try {
            Expr e = parseString(expr);

            return evaluate(e);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static int evaluate(Expr e) {
        Map<String, FinalValue> ctx = new HashMap<String, FinalValue>();
        return ((Number) e.evaluate(ctx)).getValue();
    }
}
