package kyo

import kyo._
import kyo.ios._
import scala.io._
import os._

object files {

  type Part = String | Files

  class Files(val path: List[String]) {

    def parts: List[Part] = path

    def osPath = path.foldLeft(os.pwd)(_ / _)

    def read: String < IOs =
      IOs(os.read(osPath))

    def read(
        charSet: Codec = java.nio.charset.StandardCharsets.UTF_8,
        offset: Long = 0,
        count: Int = Int.MaxValue
    ): String < IOs =
      IOs(os.read(osPath, charSet, offset, count))

    def readBytes: Array[Byte] < IOs =
      IOs(os.read.bytes(osPath))

    def readBytes(
        offset: Long = 0,
        count: Int = Int.MaxValue
    )(path: String*): Array[Byte] < IOs =
      IOs(os.read.bytes(osPath, offset, count))

    def readLines: IndexedSeq[String] < IOs =
      IOs(os.read.lines(osPath))

    def readLines(
        charSet: Codec = java.nio.charset.StandardCharsets.UTF_8
    )(path: String*): IndexedSeq[String] < IOs =
      IOs(os.read.lines(osPath, charSet))

    def truncate(size: Long): Unit < IOs =
      IOs(os.truncate(osPath, size))

    def append(value: String, perms: PermSet = null, createFolders: Boolean = true) =
      IOs(os.write.append(osPath, value, perms, createFolders))

    def write(value: String, perms: PermSet = null, createFolders: Boolean = true) =
      IOs(os.write(osPath, value, perms, createFolders))

    def list: IndexedSeq[Files] < IOs =
      list(true)

    def list(sort: Boolean): IndexedSeq[Files] < IOs =
      IOs(os.list(osPath, sort).map(p => new Files(p.segments.toList)))

    def isDir: Boolean < IOs =
      IOs(os.isDir(osPath))

    def isFile: Boolean < IOs =
      IOs(os.isFile(osPath))

    def isLink: Boolean < IOs =
      IOs(os.isLink(osPath))

    def mkDir: Unit < IOs =
      IOs(os.makeDir.all(osPath))

    def move(
        to: Files,
        replaceExisting: Boolean = false,
        atomicMove: Boolean = false,
        createFolders: Boolean = true
    ) =
      IOs(os.move(osPath, to.osPath, atomicMove, createFolders))

    def copy(
        to: Files,
        followLinks: Boolean = true,
        replaceExisting: Boolean = false,
        copyAttributes: Boolean = false,
        createFolders: Boolean = true,
        mergeFolders: Boolean = false
    ): Unit < IOs =
      IOs(os.copy(
          osPath,
          to.osPath,
          followLinks,
          replaceExisting,
          copyAttributes,
          createFolders,
          mergeFolders
      ))

    def remove: Boolean < IOs =
      remove(false)

    def remove(checkExists: Boolean): Boolean < IOs =
      IOs(os.remove(osPath, checkExists))

    def removeAll: Unit < IOs =
      IOs(os.remove.all(osPath))

    def exists: Boolean < IOs =
      exists(true)

    def exists(followLinks: Boolean): Boolean < IOs =
      IOs(os.exists(osPath, followLinks))

    override def toString = s"Files(\"${path.mkString("/")}\")"
  }

  object Files {

    def apply(path: List[Part]): Files = {
      def loop(path: List[Part], acc: List[String]): List[String] =
        path match {
          case Nil =>
            acc.reverse
          case h :: t =>
            h match {
              case h: String =>
                loop(t, h :: acc)
              case h: Files =>
                loop(h.path ::: t, acc)
            }
        }
      new Files(loop(path, Nil))
    }

    def apply(path: Part*): Files =
      apply(path.toList)
  }

}
