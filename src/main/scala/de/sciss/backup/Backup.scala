/*
 *  Backup.scala
 *  (Backup)
 *
 *  Copyright (c) 2014-2018 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU General Public License v3+
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss.backup

import java.awt.Color
import java.awt.datatransfer.DataFlavor
import java.io.{ByteArrayOutputStream, FileOutputStream}
import javax.swing.TransferHandler
import javax.swing.TransferHandler.TransferSupport

import de.sciss.desktop.impl.{SwingApplicationImpl, WindowHandlerImpl, WindowImpl}
import de.sciss.desktop.{Desktop, FileDialog, LogPane, Menu, OptionPane, Window, WindowHandler}
import de.sciss.file._

import scala.concurrent.{ExecutionContext, Future, blocking}
import scala.swing.Swing._
import scala.swing.{BorderPanel, Button, FlowPanel, Label}
import scala.util.matching.Regex
import scala.util.{Failure, Success}

object Backup extends SwingApplicationImpl[Unit]("Backup") {
  val initVolume   : String   = "Mnemo1"  // Mnemo1 or Mnemo2
  val volumes      : File     = if (Desktop.isMac) file("/Volumes") else file("/media") / sys.props("user.name")
  val initTargetDir: File     = volumes / initVolume / "CDs"

  val ejectDVD     : Boolean  = true
  val dvdDrive     : String   = "cdrom"
  val dvdDir       : File     = file("/media") / dvdDrive //  / "*"
  val shell        : String   = "/bin/bash"
  val askPass      : String   = "/usr/bin/ssh-askpass"
  val sudo         : String   = "sudo"
  val cp           : String   = "cp"
  val touch        : String   = "touch"
  val chmod        : String   = "chmod"
  val eject        : String   = "eject"
  val volName      : String   = "volname"
  val VolNameExp   : Regex    = "(\\S+)\\s*\\n*".r // somehow 'volname' has trailing spaces and a line feed

  lazy val menuFactory = Menu.Root()

  private lazy val wh = new WindowHandlerImpl(this, menuFactory)

  private var ongoing: Future[Int] = Future.successful(0)

  private val colrFg = Color.white
  private val colrBg = Color.darkGray

  private var targetDir = initTargetDir

  private var batch = List.empty[File]

  override def init(): Unit = {
    de.sciss.submin.Submin.install(true)

    val initText    = "Drop Volume to Backup"
    lazy val ggSink = new Label(initText)
    val log         = LogPane(rows = 20, columns = 80)

    def process(source: File, name: String, callEject: Boolean): Boolean = {
      if (!ongoing.isCompleted) return false

      val target = targetDir / name

      val title = s"Archiving '$name'"

      def bail(s: String): Unit = {
        val opt = OptionPane.message(s, OptionPane.Message.Error)
        opt.show(Some(fr), title)
      }

      if (!targetDir.isDirectory) {
        bail(s"Backup directory '$targetDir' not found")
        ggSink.text = initText
        return false
      }

      if (target.exists()) {
        bail(s"Target '$target' already exists")
        ggSink.text = initText
        return false
      }

      ggSink.text = s"Processing '$name'..."

      val scr = s"""#!$shell
                   |$cp -Rpv \"$source\" \"$target\"
                   |$chmod -R u-w \"$target\"
                   |$touch -amr \"$source\" \"$target\"
                   |${if (callEject) s"$eject $dvdDrive" else ""}
                   |""".stripMargin
      val scrF = File.createTemp(suffix = ".sh")
      val scrS = new FileOutputStream(scrF)
      scrS.write(scr.getBytes("utf8"))
      scrS.close()

      import ExecutionContext.Implicits.global
      import sys.process._
      val pb = Process(List(sudo, "-A", "sh", scrF.path), None, "SUDO_ASKPASS" -> askPass) #> log.outputStream
      log.clear()
      ggCD.enabled = false

      ongoing = Future(blocking(pb.!))
      val strFut = ongoing.map {
        case 0    => s"Backup of '$name' succeeded."
        case code => s"Backup of '$name' failed with code $code."
      } recover {
        case ex   => s"${ex.getClass.getSimpleName} - ${ex.getMessage}"
      }
      strFut.foreach(s => onEDT {
        ggSink.text = s
        ggCD.enabled = true
      })

      ongoing.onComplete {
        case Success(code) if code != 0 =>
            onEDT {
            bail(s"Copy process returned with code $code")
          }
        case Failure(ex) =>
          onEDT {
            wh.showDialog(Some(fr), ex -> title)
          }
        case _ =>
          onEDT {
            batch match {
              case head :: tail =>
                batch = tail
                process(source = head, name = head.base, callEject = callEject)
              case _ =>
            }
          }
      }
      true
    }

    lazy val ggCD: Button = Button("DVD-ROM") {
      import sys.process._
      val os   = new ByteArrayOutputStream
      val code = (volName #> os).!
      os.close()
      if (code == 0) {
        val VolNameExp(name) = os.toString("utf8")
        batch = Nil
        process(dvdDir.getCanonicalFile, name, callEject = ejectDVD)
      } else {
        val opt = OptionPane.message("No DVD in drive", OptionPane.Message.Error)
        opt.show(Some(fr))
      }
    }

    // NOTE: on Linux must use JDK 7 to offer javaFileListFlavor!
    lazy val th: TransferHandler = new TransferHandler {
      override def canImport(support: TransferSupport): Boolean =
        support.isDataFlavorSupported(DataFlavor.javaFileListFlavor)

      override def importData(support: TransferSupport): Boolean =
        canImport(support) && {
          import scala.collection.JavaConverters._
          val data: List[File] = support.getTransferable.getTransferData(DataFlavor.javaFileListFlavor)
            .asInstanceOf[java.util.List[File]].asScala.toList.sortBy(_.lastModified())
          data match {
            case head :: tail =>
              batch = tail
              process(head, head.base, callEject = false)
            case _ => false
          }
        }
    }

    lazy val dSink = ggSink.preferredSize
    ggSink.border = CompoundBorder(CompoundBorder(EmptyBorder(8), BeveledBorder(Lowered)), EmptyBorder(8))
    dSink.width  += 36
    dSink.height += 36
    ggSink.preferredSize = dSink   // bug in WebLaF
    ggSink.peer.setTransferHandler(th)
    ggSink.foreground = colrFg
    ggSink.background = colrBg
    ggCD.focusable  = false

    log.background  = colrBg
    log.foreground  = colrFg
    log.component.focusable = false

    lazy val lbTarget = new Label(targetDir.path)
    lazy val ggTarget = Button("Change...") {
      FileDialog.folder(init = Some(targetDir)).show(Some(fr)).foreach { f =>
        targetDir = f
        lbTarget.text = f.path
      }
    }
    lazy val pTarget  = new FlowPanel(new Label("Target Directory:"), lbTarget, ggTarget)

    lazy val pTop: BorderPanel = new BorderPanel {
      add(pTarget, BorderPanel.Position.North )
      add(ggSink , BorderPanel.Position.Center)
      add(ggCD   , BorderPanel.Position.East  )
      foreground = colrFg
      background = colrBg
    }

    lazy val fr: Window = new WindowImpl {
      def handler: WindowHandler = wh

      title     = "Backup | Archiving"
      alwaysOnTop = true

      contents  = new BorderPanel {
        background = colrBg
        add(pTop         , BorderPanel.Position.North )
        add(log.component, BorderPanel.Position.Center)
      }
      pack()

      closeOperation = Window.CloseIgnore
      reactions += {
        case Window.Closing(_) =>
          val doQuit = ongoing.isCompleted || {
            val opt = OptionPane.confirmation("Ongoing backup process. Really abort and quit?")
            opt.show(Some(fr), "Quit") == OptionPane.Result.Yes
          }
          if (doQuit) sys.exit(0)
      }
    }
    fr.front()
  }
}
