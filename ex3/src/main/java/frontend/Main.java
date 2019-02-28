package frontend;

import minijava.ast.MJProgram;

import java.io.FileReader;
import java.io.StringReader;
import java.util.Scanner;

import analysis.Analysis;
import analysis.TypeError;

public class Main {
    public static void main(String[] args) throws Exception {
        String fileName;
        if (args.length > 0) {
            fileName = args[0];
        } else {
            System.out.println("Enter a filename: ");
            fileName = "testfile.java";// new Scanner(System.in).nextLine();
        }
        try (FileReader r = new FileReader(fileName)) {
            MJFrontend frontend = new MJFrontend();
            MJProgram prog = frontend.parse(r);
            System.out.println(prog);
            Analysis analysis = new Analysis(prog);
            analysis.check();
            for (TypeError error : analysis.getTypeErrors())
                System.out.println(error.getMessage());

            frontend.getSyntaxErrors().forEach(System.out::println);
        }

    }

    public static MJProgram parseToAST(String input) throws Exception {
        MJFrontend parser = new MJFrontend();
        return parser.parse(new StringReader(input));

    }
}
