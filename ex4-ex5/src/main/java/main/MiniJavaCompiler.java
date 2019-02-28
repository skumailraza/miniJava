package main;

import analysis.Analysis;
import analysis.TypeError;
import frontend.MJFrontend;
import frontend.SyntaxError;
//import languageserver.MinijavaLanguageserver;
import minijava.ast.MJProgram;
import minillvm.ast.Prog;
import translation.Translator;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Scanner;

public class MiniJavaCompiler {

    private MJProgram javaProgram;
    private Prog llvmProg;
    private Analysis analysis;
    private MJFrontend frontend;

    public static void main(String[] args) throws Exception {
        List<String> argList = Arrays.asList(args);
        /*
         * if (argList.contains("-languageServer")) { MinijavaLanguageserver.start();
         * return; }
         */
        String fileName;
        if (args.length > 0) {
            fileName = args[0];
        } else {
            System.out.println("Enter a filename: ");
            fileName = new Scanner(System.in).nextLine();
        }
        MiniJavaCompiler compiler = new MiniJavaCompiler();
        File inputFile = new File(fileName);
        compiler.compileFile(inputFile);

        if (!compiler.getSyntaxErrors().isEmpty() || !compiler.getTypeErrors().isEmpty()) {
            compiler.getSyntaxErrors().forEach(System.out::println);
            compiler.getTypeErrors().forEach(System.out::println);
            System.exit(7);
        }

        compiler.compileLLVMCode(inputFile.getName());

    }

    public void compileFile(File file) throws Exception {
        try (FileReader r = new FileReader(file)) {
            compile(file.getPath(), r);
        }

    }

    public void compileString(String inputName, String input) throws Exception {
        compile(inputName, new StringReader(input));
    }

    public void compile(String inputName, Reader input) throws Exception {
        frontend = new MJFrontend();
        javaProgram = frontend.parse(input);
        if (!frontend.getSyntaxErrors().isEmpty()) {
            return;
        }

        // typecheck
        analysis = new Analysis(javaProgram);
        analysis.check();
        if (!analysis.getTypeErrors().isEmpty()) {
            return;
        }

        // translate
        Translator translator = new Translator(javaProgram, analysis.getTranslationInfo());
        llvmProg = translator.translate();

        // optimize
        // new SSATransformation().transformProg(llvmProg);
        // new ConstantPropagation().optimizeProgram(llvmProg);
        // new DeadCodeElimination().optimizeProgram(llvmProg);

    }

    public MJProgram getJavaProgram() {
        return javaProgram;
    }

    public Prog getLlvmProg() {
        return llvmProg;
    }

    public List<SyntaxError> getSyntaxErrors() {
        return frontend.getSyntaxErrors();
    }

    public List<TypeError> getTypeErrors() {
        if (analysis == null) {
            return Collections.emptyList();
        }
        return analysis.getTypeErrors();
    }

    private void compileLLVMCode(String name) throws IOException, InterruptedException {

        File llvmOutFile = new File(name + ".ll");
        Files.write(llvmOutFile.toPath(), llvmProg.toString().getBytes(StandardCharsets.UTF_8));

        File folder = llvmOutFile.getParentFile();

        // llvm -> bitcode
        executeCommand("llvm-as", llvmOutFile.getAbsolutePath());

        // bitcode -> object file
        File llvmBc = new File(name + ".bc");
        executeCommand("llc", "-filetype=obj", llvmBc.getAbsolutePath());

        // link object file
        File objFile = new File(name + ".o");
        executeCommand("clang", "-o", name + ".exe", objFile.getAbsolutePath());
    }

    private void executeCommand(String... args) throws IOException, InterruptedException {
        ProcessBuilder builder = new ProcessBuilder();
        builder.command(args);
        Process process = builder.start();
        int exitCode = process.waitFor();

        String out = readStream(process.getInputStream());
        System.out.println(out);
        String err = readStream(process.getErrorStream());
        System.err.println(err);

        if (exitCode != 0) {
            throw new RuntimeException("Command " + Arrays.toString(args) + " failed with error code " + exitCode);
        }
    }

    private String readStream(InputStream inputStream) {
        Scanner s = new Scanner(inputStream).useDelimiter("\\A");
        return s.hasNext() ? s.next() : "";
    }
}
