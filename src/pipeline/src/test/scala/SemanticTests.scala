import org.scalatest.FunSuite

import pipeline._
import puppet.syntax._
import puppet.graph._
import eval._

abstract class SemanticTestSuite extends FunSuite {

  val env = Facter.emptyEnv
  val fs = Ubuntu.lightweight_fs

  def runTest(program: String) {
    val graph = parse(program).desugar()
                              .toGraph(env).head._2
    val expr = pipeline.resourceGraphToExpr(graph)
    val finalStates = Ample.finalStates(fs, expr)
    assert(1 == finalStates.size)
  }
}

class FileTestSuite extends SemanticTestSuite {

  test("file without ensure with content should succeed") {
    val program = """file{"/foo":
                       content => "some contents"
                     }"""
    runTest(program)
  }

  test("single puppet file resource") {
    val program = """file{"/foo": ensure => present }"""
    runTest(program)
  }

  test("single directory") {
    val program = """file{"/tmp":
                              ensure => directory
                            }"""
    runTest(program)
  }

  test("file inside a directory") {
    val program = """file{"/tmp/foo":
                       ensure => present,
                       require => File['/tmp']
                     }
                     file{"/tmp":
                       ensure => directory
                     }"""
    runTest(program)
  }

  test("single puppet file resource with force") {
    val program = """file{"/foo":
                       ensure => file,
                       force => true
                     }"""
    runTest(program)
  }

  test("delete file resource") {
    val program = """file{"/foo": ensure => absent }"""
    runTest(program)
  }

  test("delete dir with force") {
    val program = """file {"/tmp":
                       ensure => absent,
                       force => true
                     }"""
    runTest(program)
  }

  test("link file") {
    val program = """file{"/foo":
                       ensure => link,
                       target => "/bar"
                     }"""
    runTest(program)
  }

  test("link file force") {
    val program = """file{"/foo":
                       ensure => link,
                       target => "/bar",
                       force => true
                     }"""
    runTest(program)
  }
}

class PackageTestSuite extends SemanticTestSuite {

  test("single package without attributes") {
    val program = """package{"sl": }"""
    runTest(program)
  }

  test("2 package dependent install") {
    val program = """package{"sl": }
                     package{"cmatrix":
                       require => Package['sl']
                     }"""
    runTest(program)
  }

  test("2 package concurrent install") {
    val program = """package{["cmatrix",
                              "telnet"]: }"""
    runTest(program)
  }

  test("single package remove") {
    val program = """package{"telnet":
                       ensure => absent
                    }"""
    runTest(program)
  }

  test("3 packages install") {
    val program = """package{["sl",
                              "cowsay",
                              "cmatrix"]: }"""
    runTest(program)
  }
}

class UserTestSuite extends SemanticTestSuite {

  test("single group creation") {
    val program = """group{"thegroup": ensure => present }"""
    runTest(program)
  }

  test("single user creation") {
    val program = """user{"abc": ensure => present }"""
    runTest(program)
  }

  test("concurrent group creation") {
    val program = """group{["a", "b"]: ensure => present }"""
    runTest(program)
  }

  test("concurrent user creation") {
    val program = """user{["abc", "def"]: ensure => present }"""
    runTest(program)
  }

  test("user remove") {
    val program = """user{"abc":
                       ensure => absent
                    }"""
    runTest(program)
  }
}
