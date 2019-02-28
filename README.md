# miniJava
- Implementation of MiniJava Compiler as part of the project for the Compilers and Language Processing Tools course at Technical University of Kaiserslautern. 

- Each ex_ folder contains the incremental implementation of MiniJava Compiler, from Parser to LLVM Translation and optimizations, as per the exercise sheets.

### The exercise specification sheets are in ex_specifications folder.

Supervisor: Dr. Annette Bieniusa
Exercise Manager: M.Sc. Peter Zeller

# TU Kaiserslautern

## Fachbereich Informatik

## AG Softwaretechnik

# Course: Compiler and Language Processing Tools (WS 2018)

## MiniJava

Starting with this exercise you will build a compiler for MiniJava.

MiniJava is (syntactically) a subset of Java. The semantics of a MiniJava program
is given by its semantics as a Java program. There are some restrictions, which will be
relevant for the upcoming exercises:

- Overloading of methods is not supported in MiniJava.
- The MiniJava statementSystem.out.println( ... );can only print integers.
- There are no exceptions; instead of throwing aNullPointerException,ArithmeticException,
    orIndexOutOfBoundsexception, the program will terminate.
- There is no garbage collection.
- There are no methods defined on the class Object.
- The arguments of the main method cannot be used.
- The Java standard library is not available and thejava.langpackage is not
    automatically imported.
- The Main-class cannot be instantiated.

## Lexcial structure

- All the Java keywords are considered to be keywords in MiniJava as well (see
    https://docs.oracle.com/javase/specs/jls/se11/html/jls-3.html#jls-3.9).
- In addition MiniJava uses the keywordsmain,String,length,Sytem,outand
    println, meaning that these words cannot be used as identifiers.
- There is the literalnullfor Objects.
- There are Boolean literalstrueandfalse.
- The supported symbols can be found in the grammar below.
- An identifier is a sequence of letters, digits, and underscores, starting with a
    letter.
- Integer literals will be only given in decimal notation and without suffix.
- To simplify parsing, we treat “[ ]” as a single token.
- White space such as spaces, tabs and new lines are ignored.
- There are two kinds of comments in MiniJava:
    - block comments (/* text */) where all the text from the ASCII characters
       /*to the ASCII characters*/is ignored
    - end-of-line comments (// text) where all the text from the ASCII charac-
       ters//to the end of the line is ignored.


## Grammar

The grammar below uses the notationx∗to denote thatxmight appear an arbitrary
number of times, and the notationx?, which means thatxis optional.

```
Program → MainClass ClassDecl*
MainClass → class id { public static void main ( String [ ]id ) Block}
ClassDecl → class id {MemberDecl* }
| class id extendsid {MemberDecl* }
MemberDecl → VarDecl
| MethodDecl
VarDecl → Type id ;
MethodDecl → Type id (ParamList?)Block
ParamList → Type id ParamRest*
ParamRest → ,Type id
BaseType → boolean
| int
| id
Type → BaseType
| Type [ ]
Block → {BlockStatement*}
BlockStatement → Statement
| Type id;
Statement → Block
| if (Exp) Statement elseStatement
| while (Exp)Statement
| returnExp;
| System. out. println (Exp ) ;
| Exp;
| ExpL=Exp;
Exp → Exp op Exp
| !Exp
| -Exp
| Exp. length
| Exp .id(ExpList?)
| true
| false
| 〈integer literal〉
| this
| null
| new BaseType [Exp] [ ]*
| new id ( )
| (Exp)
| ExpL
ExpL → id
| Exp[Exp]
| Exp .id
ExpList → Exp ExpRest*
ExpRest → , Exp
id → 〈identifier〉
op → &&|+|-|*|/|<|==
```



## Type rules of MiniJava

MiniJava is a strongly typed language with explicit types. This means that the type
of every variable and every expression is known at compile-time. Detecting type-errors
early (i.e. at compile time) supports programmers in writing (fail-)safe code and enables
tool support like sound refactoring and autocompletions.

MiniJava adheres for the most part to the type rules of Java. It provides two basic
types for booleans and integers, and two reference types for integer arrays and objects.
Moreover, there is a special typenull, which is a subtype of every class type and of
the array type, and there is the type⊥, which does not have any values. Types are
defined as follows:
τ::=int|bool|τ[]|C|null|⊥

for allC∈dom(CT) where the class tableCT is a mapping from class names to class
declarations.

There exists a subtype relation≺between the types. This relation is reflexive and
transitive (but not symmetric). For simplicity, we identify the class name with the class
type here.

```
τ≺τ
```
```
(refl)
```
```
τ 1 ≺τ 2 τ 2 ≺τ 3
τ 1 ≺τ 3
```
```
(trans)
```
```
C∈dom(CT)
null≺C
```
```
(null-class)
```
```
null≺τ[]
```
```
(null-array)
```
```
CT(C) =class C extends D { ... }
C≺D
```
```
(extends)
```
Remark:One major difference between Java and MiniJava is that MiniJava does not
specifyObjectto be the superclass of all other classes.

Type judgments define whether an expression, a statement, etc. iswell-typed. For
expressions, we use the type judgment Γ`ee:τto say that an expressioneis well-typed
in Γ and has typeτ. The typing context (or type environment) Γ is a set containing all
fields and local variables with their respective types which are defined when typing the
expression. For a local variablexof typeτwe have items (l,x:τ)∈Γ. For a fields,
we write (f,x:τ)∈Γ instead. The environment Γ also contains the return type (we
write (return:τ)∈Γ) and the current type ofthis(written as (this:τ)∈Γ).


```
For expressions we have the following type rules:
Γ`ee 1 :int Γ`ee 2 :int
Γ`ee 1 +e 2 :int
```
```
(plus)
```
```
Γ`ee 1 :int Γ`ee 2 :int
Γ`ee 1 −e 2 :int
```
```
(minus)
```
```
Γ`ee 1 :int Γ`ee 2 :int
Γ`ee 1 ∗e 2 :int
```
```
(mult)
```
```
Γ`ee 1 :int Γ`ee 2 :int
Γ`ee 1 /e 2 :int
```
```
(div)
```
```
Γ`ee 1 :int Γ`ee 2 :int
Γ`ee 1 < e 2 :bool
```
```
(less)
```
```
Γ`ee 1 :τ 1 Γ`ee 2 :τ 2 (τ 1 ≺τ 2 ∨τ 2 ≺τ 1 )
Γ`ee 1 ==e 2 :bool
```
```
(eq)
```
```
Γ`ee:bool
Γ`e!e:bool
```
```
(neg)
```
```
Γ`ee:int
Γ`e −e:int
```
```
(uminus)
```
```
Γ`ee 1 :τ[] Γ`ee 2 :int
Γ`ee 1 [e 2 ] :τ
```
```
(array-lookup)
```
```
Γ`ee:τ[]
Γ`ee.length:int
```
```
(array-len)
```
```
Γ`ee:C (f,x:τ)∈fields(C)
Γ`ee.x:τ
```
```
(field-access)
```
```
Γ`ee:C paramsT(m,C) = (τ 1 ,...,τn)
returnT(m,C) =τ ∀i∈{ 1 ,...,n}: Γ`eei:σi, σi≺τi
Γ`ee.m(e 1 ,...,en) :τ
```
```
(method-call)
```
```
Γ`etrue:bool
```
```
(true)
Γ`efalse:bool
```
```
(false)
```
```
(,id:τ)∈Γ
Γ`eid:τ
```
```
(var-use)
```
```
Γ`ei:int
```
```
(int-literal)
```
```
(this:τ)∈Γ
Γ`ethis:τ
```
```
(this)
Γ`enull:null
```
```
(null)
```
```
Γ`ee:int
Γ`enewτ[e][]n:τ[][]n
```
```
(new-array)
```
```
C∈dom(CT)
Γ`enewC() :C
```
```
(new-obj)
```
Because statements do not have a type in MiniJava, we use a different judgment,
Γ`ss, to denote well-typed statements and Γ`slsfor well-typed lists of statements.
To handle local variable declarations, we interpret a sequence of statementss 1 ;s 2 ;...sn
as being either the empty sequenceor as consisting of one statement followed by a
sequence of statements:s 1 ; (s 2 ;...;sn). This way, we can split off the first statement
and handle it differently, if it is a variable declaration.

We use the notation “Γ,(,x,τ)” to denote the updated type environment Γ, were
all previous entries forxhave been removed and replaced by the mapping (,x,τ).

```
The corresponding type rules then have the following form:
```
```
Γ,(l,x:τ)`sls ∀τ′: (l,x:τ′)∈/Γ
Γ`slτ x;s
```
```
(var-decl)
```
```
Γ`ss Γ`slr
Γ`sls;r
```
```
(seq)
```
```
Γ`sl
```
```
(empty)
```
```
Γ`sls
Γ`s{s}
```
```
(block)
```
```
Γ`ee:bool Γ`ss 1 Γ`ss 2
Γ`sif(e)s 1 elses 2
```
```
(if)
```
```
Γ`ee:bool Γ`ss
Γ`swhile(e)dos
```
```
(while)
```

```
Γ`ee:τ 1 τ 1 ≺τ 2 (return:τ 2 )∈Γ
Γ`sreturne;
```
```
(return)
```
```
Γ`ee:int
Γ`sSystem.out.println(e);
```
```
(print)
```
```
Γ`ee:τ Expressioneallowed as statement
Γ`se;
```
```
(stmt-expr)
```
```
Γ`ee 1 :τ 1 Γ`ee 2 :τ 2 τ 2 ≺τ 1
Γ`se 1 =e 2 ;
```
```
(assign)
```
Further, a class is well-typed if all its methods are well-typed. A method is well-typed
if its body is well-typed. When type-checking a class or method,thismust be entered
with the correct type in the typing context Γ, as well as all fields of the class and
the respective formal parameters and local variables of the method. These additional
conditions are given below:

To increase readability, we usexto denote the sequencex 1 ,...,xn, where allxihave
different names. Similarly,(f,x:τ) stands for (f,x 1 :τ 1 ),...,(f,xn:τn), and so on.

Class typing

```
(l,p:⊥)`ss
`cclassC{public static void main(String[]p)s}
```
```
(main-class)
```
```
∀mi∈m: (f,x:τ),(this:C)`mmi
`cclassC{τ x;m}
```
```
(class-no-extends)
```
```
∀mi∈m: fields(C),(this:C)`mmi ∀mi∈m: override(mi,C,D)
`cclassCextendsD{τ x;m}
```
```
(class)
```
Method typing

```
Γ,(l,p:τ),(return:τ)`ss
Γ`mτ m(τ p)s
```
```
(method)
```
Auxiliary definitions

```
CT(C) =classC{τ x;m}
fields(C) =(f,x:τ)
```
```
(fields1)
```
```
CT(C) =classCextendsD{τ x;m}
fields(C) =fields(D),(f,x:τ)
```
```
(fields2)
```
```
def(m,D) implies
returnT(m,C)≺returnT(m,D), paramsT(m,C) =paramsT(m,D)
override(m,C,D)
```
```
(override)
```
```
CT(C) =classC{τ x;m} mdefined inm
def(m,C)
```
```
(def1)
```

```
CT(C) =classCextendsD{τ x;m} mdefined inm
def(m,C)
```
```
(def2)
```
CT(C) =classCextendsD{τ x;m} mnot defined inm def(m,D)

```
def(m,C)
```
```
(def3)
```
```
def(mi,C) mi=τ m(τ p)s
paramsT(m,C) = (τ)
```
```
(paramsT)
```
```
def(mi,C) mi=τ m(τ p)s
returnT(m,C) =τ
```
```
(returnT)
```

You can find test-cases for each exercises in ex?_tests.zip. The test cases consist
of files with type errors in the foldertestdata/typechecker/errorand files without
type errors in the foldertestdata/typechecker/ok. The tests assume that you have a
classAnalysisin packageanalysis, which provides certain methods and constructors.
You may adapt the test code to fit to your interface.


## 1 The miniLLVM intermediate language

MiniLLVM is an intermediate language which uses static single assignment (SSA) form
and is modeled as a subset of LLVM with some macros to simplify the translation.

For this exercise you should install version 3.8, 3.9 or 4.0 of LLVM. The executables
optandllishould be either in yourPATH, or in a folder given by theLLVM_COMPILER_PATH
environment variable.

For Mac, the easiest way to install LLVM is using Homebrew (https://brew.sh/).
After installing Homebrew itself using the installation instructions on the Homebrew
homepage, you can install LLVM with the commandbrew install llvm. Since the
LLVM binaries are not installed into the PATH, you need to set theLLVM_COMPILER_PATH
environment variable to the installation path (usually/usr/local/opt/llvm/bin).

Under Linux, you will most likely find LLVM packages in your package manager.
Make sure that you install a package that installs the required links as well (e.g.llvm
under Ubuntu, notllvm-3.8). The LLVM executables need to be callable without
version postfix (e.g.lli, notlli-3.8).

If you are using Windows, you can have to install the ClangOnWin distribution^1 for
the two required executablesopt.exeandlli.exeto be installed.

### 1.1 Further Resources for LLVM

- Reference manual
    [http://llvm.org/docs/LangRef.html](http://llvm.org/docs/LangRef.html)
- LLVM tutorial (the Kaleidoscope language)
    [http://llvm.org/docs/tutorial/index.html](http://llvm.org/docs/tutorial/index.html)
- Hints on translating high-level constructs to LLVM
    https://github.com/idupree/llvm-doc/blob/master/MappingHighLevelConstructsToLLVMIR.rst

### 1.2 Commands for using LLVM on the commandline

Usually LLVM is integrated into compilers as a library, but it is also possible to use
it from the commandline (which we will do in this project). The following commands
show the most common use cases:

# Compile C file test.c to llvm:
clang test.c -S -emit-llvm

# run file test.ll in the llvm interpreter
lli test.ll

# Optimize a file:
opt -O3 -S test.ll > test2.ll

# Remove alloca instructions (convert to SSA form)
opt -mem2reg -S test.ll > test2.ll

# Compile an llvm file to an executable:
# 1. llvm -> bitcode
llvm-as blub.ll
# 2. bitcode -> object file
llc -filetype=obj blub.bc

(^1) available underhttps://sourceforge.net/projects/clangonwin/files/MsvcBuild/4.0/


# 3. link object file:
clang -o blub blub.o
# Show control flow graph / dominator tree (requires graphviz)
opt -analyze -view-cfg test.ll
opt -analyze -view-cfg-only test.ll
opt -analyze -view-dom-only test.ll
opt -analyze -domfrontier test.ll

