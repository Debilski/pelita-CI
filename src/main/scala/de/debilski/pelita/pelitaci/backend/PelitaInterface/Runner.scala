package de.debilski.pelita.pelitaci.backend
package PelitaInterface

import scalaz.effect.IO

trait Game {
  def path: java.io.File
  def exe: String
  def run(team1: String, team2: String, controller: Option[String]=None, subscriber: Option[String]=None): scala.sys.process.ProcessBuilder
}

trait PelitaGame extends Game {
  def path = new java.io.File("/Volumes/Data/Projects/pelita")
  def exe = "./pelitagame"

  def cmd(team1: String, team2: String, controller: Option[String]=None, subscriber: Option[String]=None) = {
    val c = controller.map("--controller" :: _ :: "--external-controller" :: Nil).getOrElse(Nil)
    val p = subscriber.map("--publish" :: _ :: Nil).getOrElse("--no-publish" :: Nil)
    exe :: team1 :: team2 :: "--null" :: "--parseable-output" :: c ::: p
  }
  
  def run(team1: String, team2: String, controller: Option[String]=None, subscriber: Option[String]=None) = {
    scala.sys.process.Process(cmd(team1, team2, controller, subscriber), cwd=path)
  }
}

trait DummyGame extends PelitaGame {
  override def exe = "echo"
  override def run(team1: String, team2: String, controller: Option[String]=None, subscriber: Option[String]=None) = super.run(team1, team2, controller, subscriber) #&& ("echo" :: "1" :: Nil)
}

trait ShortGame extends PelitaGame {
  override def cmd(team1: String, team2: String, controller: Option[String]=None, subscriber: Option[String]=None) = super.cmd(team1, team2, controller, subscriber) ::: ("--rounds" :: "3" :: Nil)
}

abstract class Runner {
  type GameType <: Game
  val game: GameType
  
  def cloneRepo(uri: String, cwd: java.io.File): scala.sys.process.ProcessBuilder = {
    import scala.sys.process.Process

    val repo = new java.net.URI(uri)

    val commit = repo.getFragment
    val repository = new java.net.URI(repo.getScheme, repo.getSchemeSpecificPart, null).toString // null???
    val depth = 2
  
    val cmd = "git" :: "clone" ::
              "--branch" :: commit ::
              "--depth" :: depth.toString ::
              "--recurse-submodules" ::
              "--" :: repository ::
              "." :: Nil

    Process(cmd, cwd=cwd)
  }
  
  def doWithTempDirectory[T](prefix: String)(action: java.nio.file.Path => IO[T]): IO[T] = {
    import java.nio.file.Files
    
    
    def cleanUp(tempDir: java.nio.file.Path) = IO { Files.walkFileTree(tempDir, new java.nio.file.SimpleFileVisitor[java.nio.file.Path] {
      override def visitFile(file: java.nio.file.Path, attrs: java.nio.file.attribute.BasicFileAttributes): java.nio.file.FileVisitResult = {
         Files.delete(file)
         java.nio.file.FileVisitResult.CONTINUE
      }
      override def postVisitDirectory(dir: java.nio.file.Path, e: java.io.IOException): java.nio.file.FileVisitResult = {
        if (e == null) {
          java.nio.file.Files.delete(dir)
          java.nio.file.FileVisitResult.CONTINUE
        } else {
          // directory iteration failed
          throw e
        }
      }
    })
    }
    
    for {
      tempDir <- IO { Files.createTempDirectory(prefix) }
      res <- action(tempDir)
      _ <- cleanUp(tempDir)
    } yield res
    
  }
  
  def playGame(pairing: Pairing, controller: Option[String]=None, subscriber: Option[String]=None): IO[Option[MatchResult]] = {
    import scala.sys.process.Process
    
    val Pairing(team1: Team, team2: Team) = pairing
    
    
    import java.nio.file.Files
    
    doWithTempDirectory("pelita-CI-") { tempDir => IO {
        val team1Path = tempDir.resolve("t1")
        val team2Path = tempDir.resolve("t2")
        
        Files.createDirectory(team1Path)
        Files.createDirectory(team2Path)
        
        cloneRepo(team1.uri, team1Path.toFile).!
        cloneRepo(team2.uri, team2Path.toFile).!
        
        val team1Spec = team1Path.resolve(team1.factory).toFile.toString
        val team2Spec = team2Path.resolve(team2.factory).toFile.toString
        
        val lines = game.run(team1Spec, team2Spec, controller, subscriber).lines
        
        lines foreach println
        val pelitaOutput = lines.lastOption
        
        pelitaOutput flatMap { _ match {
            case "-" => Some(MatchDraw)
            case "0" => Some(MatchWinnerLeft)
            case "1" => Some(MatchWinnerRight)
            case _ => None
          }
        } map (res => MatchResult(pairing, res))
      }
    }
  }
}