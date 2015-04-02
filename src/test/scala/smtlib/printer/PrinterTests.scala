package smtlib
package printer

import lexer.Lexer
import parser.Terms._
import parser.Commands._
import parser.CommandsResponses._
import parser.Parser

import common._

import java.io.StringReader

import org.scalatest.FunSuite

import scala.language.implicitConversions
import scala.annotation.tailrec

class PrinterTests extends FunSuite {

  /*
   * TODO: test the requirement from the standard:
           Any response whish is not double-quoted and not parenthesized
           should be followed by a whitespace character (new line)
   */

  override def suiteName = "Printer correctness suite"

  private implicit def strToSym(str: String): SSymbol = SSymbol(str)
  private implicit def strToId(str: String): Identifier = Identifier(SSymbol(str))
  private implicit def strToKeyword(str: String): SKeyword = SKeyword(str)
  private implicit def symToTerm(sym: SSymbol): QualifiedIdentifier = QualifiedIdentifier(sym.name)
  private implicit def intToNum(n: Int): SNumeral = SNumeral(n)
  private implicit def IntSeqToIndices(ns: Seq[Int]): Seq[Index] = ns.map(n => SNumeral(n))

  def mkTests(printer: Printer): Unit = {
    implicit val p = printer
    val printerName = printer.name
    test(printerName + ": printing simple Terms") { testSimpleTerms }
    test(printerName + ": Printing tricky Terms") { testTrickyTerms }
    test(printerName + ": Printing Symbols with weird names") { testWeirdSymbolNames }
    test(printerName + ": Printing Symbols with quoted names") { testQuotedSymbols }
    test(printerName + ": Printing composed Terms") { testComposedTerm }
    test(printerName + ": Printing Sorts") { testSorts }
    test(printerName + ": Printing single Commands") { testSingleCommands }
    test(printerName + ": Printing declare-datatypes commands") { testDeclareDatatypes }
    test(printerName + ": Printing set-option commands") { testSetOptionCommand }
    test(printerName + ": Printing set-info commands") { testSetInfoCommand }
    test(printerName + ": Printing get-info commands") { testGetInfoCommand }
    test(printerName + ": Printing simple script") { testSimpleScript }
    test(printerName + ": Printing basic commands responses") { testCommandsResponses }
    test(printerName + ": Printing get-assignment response quoted symbols") { testGetAssignmentResponseQuotedSymbol }
    test(printerName + ": Printing get-Info responses") { testGetInfoResponses }
    test(printerName + ": Printing get-model responses") { testGetModelResponse }
  }

  mkTests(RecursivePrinter)
  mkTests(TailPrinter)

  private def checkSort(sort: Sort)(implicit printer: Printer): Unit = {

    val directPrint: String = printer.toString(sort)

    val parser = Parser.fromString(directPrint)
    val parsedAgain: Sort = parser.parseSort
    val printAgain: String = printer.toString(parsedAgain)

    assert(directPrint === printAgain)
    assert(sort === parsedAgain)
  }

  private def checkTerm(term: Term)(implicit printer: Printer): Unit = {

    val directPrint: String = printer.toString(term)

    val parser = Parser.fromString(directPrint)
    val parsedAgain: Term = parser.parseTerm
    val printAgain: String = printer.toString(parsedAgain)

    assert(directPrint === printAgain)
    assert(term === parsedAgain)
  }

  private def checkCommand(cmd: Command)(implicit printer: Printer): Unit = {

    val directPrint: String = printer.toString(cmd)

    val parser = Parser.fromString(directPrint)
    val parsedAgain: Command = parser.parseCommand
    val printAgain: String = printer.toString(parsedAgain)

    assert(directPrint === printAgain)
    assert(cmd === parsedAgain)
  }

  private def checkScript(script: Script)(implicit printer: Printer): Unit = {

    val directPrint: String = printer.toString(script)

    val parser = Parser.fromString(directPrint)
    val parsedAgain: Script = parser.parseScript
    val printAgain: String = printer.toString(parsedAgain)

    assert(directPrint === printAgain)
    assert(script === parsedAgain)
  }

  private def check[A](res: A, printer: (A) => String, parser: (String) => A): Unit = {

    val directPrint: String = printer(res)

    val parsedAgain: A = parser(directPrint)
    val printAgain: String = printer(parsedAgain)

    assert(directPrint === printAgain)
    assert(res === parsedAgain)
  }


  def testSimpleTerms(implicit printer: Printer): Unit = {
    checkTerm(SNumeral(0))
    checkTerm(SNumeral(42))
    checkTerm(SHexadecimal(Hexadecimal.fromString("FF").get))
    checkTerm(SHexadecimal(Hexadecimal.fromString("123abcDeF").get))
    checkTerm(SBinary(List(true)))
    checkTerm(SBinary(List(true, false, true)))
    checkTerm(SBinary(List(false, false, true)))
    checkTerm(SString("abcd"))
    checkTerm(SString("hello-world"))
    checkTerm(QualifiedIdentifier("abc"))
    checkTerm(FunctionApplication(
            QualifiedIdentifier("f"), Seq(QualifiedIdentifier("a"), QualifiedIdentifier("b"))))
    checkTerm(Let(VarBinding("a", QualifiedIdentifier("x")), Seq(), QualifiedIdentifier("a")))

    checkTerm(ForAll(SortedVar("a", Sort("A")), Seq(), QualifiedIdentifier("a")))
    checkTerm(Exists(SortedVar("a", Sort("A")), Seq(), QualifiedIdentifier("a")))
    checkTerm(AnnotatedTerm(QualifiedIdentifier("a"), Attribute(SKeyword("note"), Some(SSymbol("abcd"))), Seq()))

  }

  def testTrickyTerms(implicit printer: Printer): Unit = {
    checkTerm(SString("abc\"def"))
    checkTerm(SString("hello \"World\""))
  }

  def testWeirdSymbolNames(implicit printer: Printer): Unit = {
    checkTerm(QualifiedIdentifier("+-/"))
    checkTerm(QualifiedIdentifier("^^^"))
    checkTerm(QualifiedIdentifier("+-/*=%?!.$_~&^<>@"))
    checkTerm(QualifiedIdentifier("$12%"))
  }

  def testQuotedSymbols(implicit printer: Printer): Unit = {
    checkTerm(QualifiedIdentifier("hey there"))
    checkTerm(QualifiedIdentifier(
"""hey there,
What are you up to man?"""))
  }

  def testComposedTerm(implicit printer: Printer): Unit = {
    checkTerm(
      FunctionApplication(
        QualifiedIdentifier("f"),
        Seq(
          FunctionApplication(
            QualifiedIdentifier("g"),
            Seq(QualifiedIdentifier("aaa"),
                QualifiedIdentifier("bb"))
          ),
          QualifiedIdentifier("c")
        )
      )
    )

    checkTerm(
      FunctionApplication(
        QualifiedIdentifier("f"),
        Seq(
          FunctionApplication(
            QualifiedIdentifier("g"),
            Seq(QualifiedIdentifier("aaa"),
                QualifiedIdentifier("bb"))
          ),
          QualifiedIdentifier("c"),
          FunctionApplication(
            QualifiedIdentifier("g"),
            Seq(QualifiedIdentifier("abcd"),
                FunctionApplication(
                  QualifiedIdentifier("h"),
                  Seq(QualifiedIdentifier("x"))
                ))
          )
        )
      )
    )
  }

  def testSorts(implicit printer: Printer): Unit = {
    checkSort(Sort(Identifier(SSymbol("A"))))
    checkSort(Sort(Identifier(SSymbol("A"), Seq(42))))
    checkSort(Sort(Identifier(SSymbol("A"), Seq(42, 23))))
    checkSort(Sort(Identifier(SSymbol("A"), Seq(42, 12, 23))))
    checkSort(Sort(Identifier(SSymbol("A")), Seq(Sort("B"), Sort("C"))))
    checkSort(Sort(Identifier(SSymbol("A"), Seq(27)), Seq(Sort("B"), Sort("C"))))
  }

  def testSingleCommands(implicit printer: Printer): Unit = {

    checkCommand(Assert(QualifiedIdentifier("true")))
    checkCommand(CheckSat())
    checkCommand(CheckSatAssuming(Seq(PropLiteral("a", true), PropLiteral("b", false))))

    checkCommand(DeclareConst("c", Sort("C")))
    checkCommand(DeclareFun("xyz", Seq(Sort("A"), Sort("B")), Sort("C")))
    checkCommand(DeclareSort("A", 0))

    checkCommand(DefineFun(FunDef("f", Seq(SortedVar("a", Sort("A"))), Sort("B"), QualifiedIdentifier("a"))))
    checkCommand(DefineFunRec(FunDef("f", Seq(SortedVar("a", Sort("A"))), Sort("B"), QualifiedIdentifier("a"))))
    checkCommand(DefineFunsRec(
      Seq(FunDec("f", Seq(SortedVar("a", Sort("A"))), Sort("B")),
          FunDec("g", Seq(SortedVar("a", Sort("A"))), Sort("B"))),
      Seq(FunctionApplication(QualifiedIdentifier("g"), Seq(QualifiedIdentifier("a"))),
          FunctionApplication(QualifiedIdentifier("f"), Seq(QualifiedIdentifier("a"))))))
    checkCommand(DefineSort("A", Seq("B", "C"), 
      Sort(Identifier("Array"), Seq(Sort("B"), Sort("C")))))

    checkCommand(Echo(SString("abcd")))
    checkCommand(Echo(SString("echoing")))
    checkCommand(Exit())

    checkCommand(GetAssertions())
    checkCommand(GetAssignment())
    checkCommand(GetInfo(AuthorsInfoFlag))
    checkCommand(GetModel())
    checkCommand(GetOption("keyword"))
    checkCommand(GetProof())
    checkCommand(GetUnsatAssumptions())
    checkCommand(GetUnsatCore())
    checkCommand(GetValue(SSymbol("x"), Seq(SSymbol("y"), SSymbol("z"))))

    checkCommand(Pop(1))
    checkCommand(Pop(2))
    checkCommand(Push(1))
    checkCommand(Push(4))

    checkCommand(Reset())
    checkCommand(ResetAssertions())

    checkCommand(SetLogic(AUFLIA))
    checkCommand(SetLogic(AUFLIRA))
    checkCommand(SetLogic(AUFNIRA))
    checkCommand(SetLogic(LRA))
    checkCommand(SetLogic(QF_UF))
    checkCommand(SetLogic(QF_LIA))
    checkCommand(SetLogic(QF_LRA))
    checkCommand(SetLogic(QF_AX))
  }

  def testDeclareDatatypes(implicit printer: Printer): Unit = {
    checkCommand(DeclareDatatypes(Seq(
      SSymbol("A") -> Seq(Constructor(SSymbol("A1"), 
                                      Seq(SSymbol("a1a") -> Sort("A"), SSymbol("a1b") -> Sort("A"))),
                          Constructor(SSymbol("A2"), 
                                      Seq(SSymbol("a2a") -> Sort("A"), SSymbol("a2b") -> Sort("A"))))
    )))
  }

  def testSetOptionCommand(implicit printer: Printer): Unit = {
    checkCommand(SetOption(DiagnosticOutputChannel("toto")))

    checkCommand(SetOption(ExpandDefinitions(true)))
    checkCommand(SetOption(ExpandDefinitions(false)))
    checkCommand(SetOption(GlobalDeclarations(true)))
    checkCommand(SetOption(GlobalDeclarations(false)))

    checkCommand(SetOption(PrintSuccess(true)))
    checkCommand(SetOption(PrintSuccess(false)))
    checkCommand(SetOption(InteractiveMode(true)))
    checkCommand(SetOption(InteractiveMode(false)))

    checkCommand(SetOption(ProduceAssertions(true)))
    checkCommand(SetOption(ProduceAssertions(false)))
    checkCommand(SetOption(ProduceAssignments(true)))
    checkCommand(SetOption(ProduceAssignments(false)))
    checkCommand(SetOption(ProduceModels(true)))
    checkCommand(SetOption(ProduceModels(false)))
    checkCommand(SetOption(ProduceProofs(true)))
    checkCommand(SetOption(ProduceProofs(false)))
    checkCommand(SetOption(ProduceUnsatAssumptions(true)))
    checkCommand(SetOption(ProduceUnsatAssumptions(false)))
    checkCommand(SetOption(ProduceUnsatCores(true)))
    checkCommand(SetOption(ProduceUnsatCores(false)))

    checkCommand(SetOption(RandomSeed(42)))
    checkCommand(SetOption(RandomSeed(12)))

    checkCommand(SetOption(RegularOutputChannel("test")))

    checkCommand(SetOption(ReproducibleResourceLimit(4)))
    checkCommand(SetOption(ReproducibleResourceLimit(1)))
    checkCommand(SetOption(Verbosity(4)))
    checkCommand(SetOption(Verbosity(1)))

    checkCommand(SetOption(AttributeOption(Attribute(SKeyword("key")))))
    checkCommand(SetOption(AttributeOption(Attribute(SKeyword("key"), Some(SString("value"))))))
  }

  def testSetInfoCommand(implicit printer: Printer): Unit = {
    checkCommand(SetInfo(Attribute("test")))
    checkCommand(SetInfo(Attribute("data")))
    checkCommand(SetInfo(Attribute("authors", Some(SString("Regis Blanc")))))
    checkCommand(SetInfo(Attribute("sources", 
Some(SSymbol(
"""This is a long quoted symbol.
It spans a couple lines""")))))
  }

  def testGetInfoCommand(implicit printer: Printer): Unit = {
    checkCommand(GetInfo(ErrorBehaviorInfoFlag))
    checkCommand(GetInfo(NameInfoFlag))
    checkCommand(GetInfo(AuthorsInfoFlag))
    checkCommand(GetInfo(VersionInfoFlag))
    checkCommand(GetInfo(ReasonUnknownInfoFlag))
    checkCommand(GetInfo(AllStatisticsInfoFlag))
    checkCommand(GetInfo(KeywordInfoFlag("custom")))
  }

  def testSimpleScript(implicit printer: Printer): Unit = {
    val script = Script(List(
      SetLogic(QF_UF),
      DeclareSort("MySort", 0),
      Push(1),
      Assert(FunctionApplication(QualifiedIdentifier("<"), Seq(SNumeral(3), SNumeral(1)))),
      CheckSat()
    ))
    checkScript(script)
  }


  def testCommandsResponses(implicit printer: Printer): Unit = {

    def printGenRes(res: GenResponse): String = printer.toString(res) 
    def parseGenRes(in: String): GenResponse = Parser.fromString(in).parseGenResponse
    check(Success, printGenRes, parseGenRes)
    check(Unsupported, printGenRes, parseGenRes)
    check(Error("symbol missing"), printGenRes, parseGenRes)

    def printCheckSat(res: CheckSatResponse): String = printer.toString(res) 
    def parseCheckSat(in: String): CheckSatResponse = Parser.fromString(in).parseCheckSatResponse
    check(CheckSatStatus(SatStatus), printCheckSat, parseCheckSat)
    check(CheckSatStatus(UnsatStatus), printCheckSat, parseCheckSat)
    check(CheckSatStatus(UnknownStatus), printCheckSat, parseCheckSat)

    def printEcho(res: EchoResponse): String = printer.toString(res) 
    def parseEcho(in: String): EchoResponse = Parser.fromString(in).parseEchoResponse
    check(EchoResponseSuccess("abcd"), printEcho, parseEcho)
    check(EchoResponseSuccess("Hello, World"), printEcho, parseEcho)

    def printGetAssertions(res: GetAssertionsResponse): String = printer.toString(res)
    def parseGetAssertions(in: String): GetAssertionsResponse = Parser.fromString(in).parseGetAssertionsResponse
    check(GetAssertionsResponseSuccess(Seq(
           QualifiedIdentifier("a"), SNumeral(42)
          )), printGetAssertions, parseGetAssertions)

    def printGetAssignment(res: GetAssignmentResponse): String = printer.toString(res)
    def parseGetAssignment(in: String): GetAssignmentResponse = Parser.fromString(in).parseGetAssignmentResponse
    check(GetAssignmentResponseSuccess(Seq(
      (SSymbol("a"), true), (SSymbol("b"), false))),
      printGetAssignment, 
      parseGetAssignment)

    def printGetOption(res: GetOptionResponse): String = printer.toString(res)
    def parseGetOption(in: String): GetOptionResponse = Parser.fromString(in).parseGetOptionResponse
    check(GetOptionResponseSuccess(SSymbol("test")), printGetOption, parseGetOption)

    def printGetProof(res: GetProofResponse): String = printer.toString(res)
    def parseGetProof(in: String): GetProofResponse = Parser.fromString(in).parseGetProofResponse
    check(GetProofResponseSuccess(SList(SSymbol("a"), SNumeral(42))),
          printGetProof, parseGetProof)

    def printGetUnsatAssumptions(res: GetUnsatAssumptionsResponse): String = printer.toString(res)
    def parseGetUnsatAssumptions(in: String): GetUnsatAssumptionsResponse = Parser.fromString(in).parseGetUnsatAssumptionsResponse
    check(GetUnsatAssumptionsResponseSuccess(Seq(SSymbol("a"))),
          printGetUnsatAssumptions, parseGetUnsatAssumptions)
    check(GetUnsatAssumptionsResponseSuccess(
            Seq(SSymbol("a"), SSymbol("b"))), 
          printGetUnsatAssumptions, parseGetUnsatAssumptions)

    def printGetUnsatCore(res: GetUnsatCoreResponse): String = printer.toString(res)
    def parseGetUnsatCore(in: String): GetUnsatCoreResponse = Parser.fromString(in).parseGetUnsatCoreResponse
    check(GetUnsatCoreResponseSuccess(Seq(SSymbol("a"))), printGetUnsatCore, parseGetUnsatCore)
    check(GetUnsatCoreResponseSuccess(
            Seq(SSymbol("a"), SSymbol("b"))), printGetUnsatCore, parseGetUnsatCore)

    def printGetValue(res: GetValueResponse): String = printer.toString(res)
    def parseGetValue(in: String): GetValueResponse = Parser.fromString(in).parseGetValueResponse
    check(GetValueResponseSuccess(Seq( 
           (SSymbol("a"), SNumeral(42)) 
          )), printGetValue, parseGetValue)
    check(GetValueResponseSuccess(Seq( 
           (SSymbol("a"), SNumeral(42)), 
           (SSymbol("b"), SNumeral(12)) 
         )), printGetValue, parseGetValue)
  }

  def testGetAssignmentResponseQuotedSymbol(implicit printer: Printer): Unit = {
    def printGetAssignment(res: GetAssignmentResponse): String = printer.toString(res)
    def parseGetAssignment(in: String): GetAssignmentResponse = Parser.fromString(in).parseGetAssignmentResponse

    check(GetAssignmentResponseSuccess(Seq(
      (SSymbol("quoted symbol"), true), (SSymbol("non-quoted-symbol"), false))),
      printGetAssignment, 
      parseGetAssignment)

  }

  def testGetInfoResponses(implicit printer: Printer): Unit = {
    def printGetInfo(res: GetInfoResponse): String = printer.toString(res)
    def parseGetInfo(in: String): GetInfoResponse = Parser.fromString(in).parseGetInfoResponse

    check(GetInfoResponseSuccess(AssertionStackLevelsInfoResponse(13), Seq()),
          printGetInfo, parseGetInfo)

    check(GetInfoResponseSuccess(AuthorsInfoResponse("Regis Blanc"), Seq()),
          printGetInfo, parseGetInfo)
    check(GetInfoResponseSuccess(AuthorsInfoResponse("Regis \"Rage\" Blanc"), Seq()),
          printGetInfo, parseGetInfo)

    check(GetInfoResponseSuccess(
            ErrorBehaviorInfoResponse(ContinuedExecutionErrorBehavior), Seq()),
          printGetInfo, parseGetInfo)
    check(GetInfoResponseSuccess(
            ErrorBehaviorInfoResponse(ImmediateExitErrorBehavior), Seq()),
          printGetInfo, parseGetInfo)

    check(GetInfoResponseSuccess(NameInfoResponse("Scala-SmtLib"), Seq()),
          printGetInfo, parseGetInfo)

    check(GetInfoResponseSuccess(ReasonUnknownInfoResponse(TimeoutReasonUnknown), Seq()),
          printGetInfo, parseGetInfo)
    check(GetInfoResponseSuccess(ReasonUnknownInfoResponse(MemoutReasonUnknown), Seq()),
          printGetInfo, parseGetInfo)
    check(GetInfoResponseSuccess(ReasonUnknownInfoResponse(IncompleteReasonUnknown), Seq()),
          printGetInfo, parseGetInfo)

    check(GetInfoResponseSuccess(VersionInfoResponse("2.13.7"), Seq()),
          printGetInfo, parseGetInfo)

    check(GetInfoResponseSuccess(AttributeInfoResponse(Attribute(SKeyword("key"))), Seq()),
          printGetInfo, parseGetInfo)
  }

  def testGetModelResponse(implicit printer: Printer): Unit = {
    def printGetModel(res: GetModelResponse): String = printer.toString(res)
    def parseGetModel(in: String): GetModelResponse = Parser.fromString(in).parseGetModelResponse
    check(
      GetModelResponseSuccess(List(DefineFun(FunDef("z", Seq(), Sort("Int"), SNumeral(0))))),
      printGetModel,
      parseGetModel
    )

    check(
      GetModelResponseSuccess(List(
        DefineFun(FunDef("z", Seq(), Sort("Int"), SNumeral(0))),
        DeclareFun("a", Seq(), Sort("A")),
        ForAll(SortedVar("x", Sort("A")), Seq(), QualifiedIdentifier("x"))
      )),
      printGetModel,
      parseGetModel
    )
  }

  test("Printing deep trees with tail printer") {
    @tailrec
    def mkDeepTerm(n: Int, t: Term): Term = 
      if(n == 0) t
      else mkDeepTerm(n-1, Let(VarBinding("x", SString("some value")), Seq(), t))

    val t1 = mkDeepTerm(10000, SString("base case"))

    //will overflow with RecursivePrinter if stack is not too big
    //RecursivePrinter.toString(t1)

    //should not generate exception
    TailPrinter.toString(t1)

    //val t2 = mkDeepTerm(100000, SString("base case"))
    //TailPrinter.toString(t2)
  }

}
