package org.velvia.filo.codecs

import scala.language.postfixOps
import scalaxy.loops._

import org.velvia.filo.{FiloVector, NaMaskAvailable, FastBufferReader}
import org.velvia.filo.vector._

/**
 * Represents either an empty column (length 0) or a column where none of the
 * values are available (null).
 */
class EmptyFiloVector[A](len: Int) extends FiloVector[A] {
  final def isAvailable(index: Int): Boolean = false
  final def foreach[B](fn: A => B): Unit = {}
  final def apply(index: Int): A =
    if (index < len) { null.asInstanceOf[A] }
    else             { throw new ArrayIndexOutOfBoundsException }
  final def length: Int = len
}

abstract class SimplePrimitiveWrapper[@specialized A](spv: SimplePrimitiveVector)
extends NaMaskAvailable[A](spv.naMask) {
  val info = spv.info
  val _len = spv.len
  val reader = FastBufferReader(spv.dataAsByteBuffer())

  final def length: Int = _len

  final def foreach[B](fn: A => B): Unit = {
    if (isEmptyMask) {   // every value available!
      for { i <- 0 until length optimized } { fn(apply(i)) }
    } else {
      for { i <- 0 until length optimized } { if (isAvailable(i)) fn(apply(i)) }
    }
  }
}

// TODO: ditch naMask
class SimpleStringWrapper(ssv: SimpleStringVector)
extends NaMaskAvailable[String](ssv.naMask) {
  val _len = ssv.dataLength

  final def length: Int = _len

  final def apply(i: Int): String = ssv.data(i)

  final def foreach[B](fn: String => B): Unit = {
    for { i <- 0 until length optimized } { if (isAvailable(i)) fn(apply(i)) }
  }
}


