package de.debilski.pelita.pelitaci.backend
package PelitaInterface

import scalaz.effect.IO
import de.debilski.pelita.pelitaci.DefaultSettings

trait Game {
  def path: java.io.File
  def exe: String
  def run(team1: String, team2: String, controller: Option[String]=None, subscriber: Option[String]=None): scala.sys.process.ProcessBuilder
  def checkName(team: String): scala.sys.process.ProcessBuilder
}

trait PelitaGame extends Game {
  def path = new java.io.File(DefaultSettings.pelitaPath)
  def exe = DefaultSettings.pelitagame

  def cmd(team1: String, team2: String, controller: Option[String]=None, subscriber: Option[String]=None) = {
    val c = controller.map("--controller" :: _ :: "--external-controller" :: Nil).getOrElse(Nil)
    val p = subscriber.map("--publish" :: _ :: Nil).getOrElse("--no-publish" :: Nil)
    exe :: team1 :: team2 :: "--null" :: "--parseable-output" :: c ::: p
  }
  
  def run(team1: String, team2: String, controller: Option[String]=None, subscriber: Option[String]=None) = {
    println(cmd(team1, team2, controller, subscriber))
    println(path)
    scala.sys.process.Process(cmd(team1, team2, controller, subscriber), cwd=path)
  }

  def checkName(team: String) = {
    println(exe :: team :: "--check-team" :: Nil)
    println(path)
    scala.sys.process.Process(exe :: team :: "--check-team" :: Nil, cwd=path)
  }
}

trait DummyGame extends PelitaGame {
  override def exe = "echo"
  override def run(team1: String, team2: String, controller: Option[String]=None, subscriber: Option[String]=None) = super.run(team1, team2, controller, subscriber) #&& ("echo" :: "1" :: Nil)
  override def checkName(team: String) = super.checkName(team) #&& ("echo" :: "DummyGame" :: Nil)
}

trait ShortGame extends PelitaGame {
  override def cmd(team1: String, team2: String, controller: Option[String]=None, subscriber: Option[String]=None) = super.cmd(team1, team2, controller, subscriber) ::: ("--rounds" :: "3" :: Nil)
}

abstract class Runner {
  type GameType <: Game
  val game: GameType
  
  def cloneRepo(url: String, cwd: java.io.File): scala.sys.process.ProcessBuilder = {
    import scala.sys.process.Process

    val repo = new java.net.URI(url)

    val commit = Option(repo.getFragment) getOrElse "master"
    val repository = new java.net.URI(repo.getScheme, repo.getSchemeSpecificPart, null).toString // null???
    val depth = 2
  
    val cmd = "git" :: "clone" ::
              "--verbose" ::
              "--branch" :: commit ::
              "--depth" :: depth.toString ::
              "--recurse-submodules" ::
              "--" :: repository ::
              "." :: Nil

    Process(cmd, cwd=cwd)
  }
  
  def withTemporaryDirectory[T](prefix: String)(action: java.nio.file.Path => IO[T]): IO[T] = {
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

  case class PreparedTeam(team: Team, teamSpec: String)
  case class PreparedGame(pairing: Pairing, teamSpecs: (String, String)) {
    def teams = (PreparedTeam(pairing.team1, teamSpecs._1), PreparedTeam(pairing.team2, teamSpecs._2))
  }

  def withPreparedTeam[T](team: Team)(f: PreparedTeam => IO[(Stream[String], Option[T])]): IO[(Stream[String], Option[T])] = {
    withTemporaryDirectory("pelita-CI-") { tempDir => IO {

      import java.nio.file.Files

      val teamPath = tempDir.resolve("t")

      Files.createDirectory(teamPath)

      val logger = scala.sys.process.ProcessLogger((o: String) => println("out " + o), (e: String) => println("err " + e))

      if (!team.url.isEmpty)
        cloneRepo(team.url, teamPath.toFile).!! (logger)

      val teamSpec = if (!team.url.isEmpty)
        teamPath.resolve(team.factory).toFile.toString
      else
        team.factory

      teamSpec
    } flatMap (teamSpec => f(PreparedTeam(team, teamSpec)))
    }}

  def withPreparedGame[T](pairing: Pairing)(f: PreparedGame => IO[(Stream[String], Option[T])]): IO[(Stream[String], Option[T])] = {
    val Pairing(team1: Team, team2: Team) = pairing

    withPreparedTeam(team1) { preparedTeam1 =>
      withPreparedTeam(team2) { preparedTeam2 =>
        val preparedGame = PreparedGame(Pairing(preparedTeam1.team, preparedTeam2.team), (preparedTeam1.teamSpec, preparedTeam2.teamSpec))
        f(preparedGame)
      }
    }
  }

  def playPreparedGame(controller: Option[String]=None, subscriber: Option[String]=None): PreparedGame => IO[(Stream[String], Option[MatchResult])] = preparedGame => IO {
    val lines = game.run(preparedGame.teamSpecs._1, preparedGame.teamSpecs._2, controller, subscriber).lines

    lines foreach println
    val pelitaOutput = lines.lastOption

    val res = pelitaOutput flatMap {
      case "-" => Some(MatchDraw)
      case "0" => Some(MatchWinnerLeft)
      case "1" => Some(MatchWinnerRight)
      case _ => None
    } map (res => MatchResult(preparedGame.pairing, res))

    (lines, res)
  }

  def playGame(pairing: Pairing, controller: Option[String]=None, subscriber: Option[String]=None): IO[(Stream[String], Option[MatchResult])] =
    withPreparedGame(pairing)(playPreparedGame(controller, subscriber))

  def checkPreparedTeamName(): PreparedTeam => IO[(Stream[String], Option[String])] = preparedTeam => IO {
    val lines = game.checkName(preparedTeam.teamSpec).lines

    lines foreach println

    (lines, lines.lastOption)
  }

  def checkTeamName(team: Team): IO[(Stream[String], Option[String])] =
    withPreparedTeam(team)(checkPreparedTeamName())
}
