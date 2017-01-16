package org.velvia.filo.codecs

import com.google.flatbuffers.{FlatBufferBuilder, Table}
import java.nio.{ByteBuffer, ByteOrder}
import scala.collection.mutable.BitSet

import org.velvia.filo.vector._
import org.velvia.filo.WireFormat

/**
 * Common utilities for creating FlatBuffers, including mask and data vector building
 */
object Utils {
  // default initial size of bytebuffer to allocate.  flatbufferbuilder will expand the buffer if needed.
  // don't make this too big, because one buffer needs to be allocated per column, and writing many columns
  // at once will use up a ton of memory otherwise.
  val BufferSize = 64 * 1024
  val SizeOfInt = 4

  // Returns true if every element in the data to be encoded is marked as NA (mask is set)
  def isAllNA(mask: BitSet, dataLength: Int): Boolean = mask.size == dataLength

  // Ensures that the resulting array(long) has at least as many bits as numBits
  def makeBitMask(mask: BitSet, numBits: Int): Array[Long] = {
    val initialBits = mask.toBitMask
    val numLongs = roundUp(numBits, 64) / 64
    initialBits ++ Array.fill(numLongs - initialBits.size)(0L)
  }

  // @return offset of mask table
  def populateNaMask(fbb: FlatBufferBuilder, mask: BitSet, dataLen: Int): Int = {
    val empty = mask.size == 0
    val full = isAllNA(mask, dataLen)
    var bitMaskOffset = 0

    // Simple bit mask, 1 bit per row
    // One option is to use JavaEWAH compressed bitmaps, requires no deserialization now
    // RoaringBitmap is really cool, but very space inefficient when you have less than 4096 integers
    //    it's much better when you have 100000 or more rows
    // NOTE: we cannot nest structure creation, so have to create bitmask vector first :(
    if (!empty && !full) bitMaskOffset = NaMask.createBitMaskVector(fbb, makeBitMask(mask, dataLen))

    NaMask.startNaMask(fbb)
    NaMask.addMaskType(fbb, if (full)       { MaskType.AllOnes }
                            else if (empty) { MaskType.AllZeroes }
                            else            { MaskType.SimpleBitMask })

    if (!empty && !full) NaMask.addBitMask(fbb, bitMaskOffset)
    NaMask.endNaMask(fbb)
  }

  private final def roundUp(n: Int, align: Int): Int = ((n + (align - 1)) / align) * align

  /**
   * Sets up and closes the FlatBuffer [ubyte] vector inside of many Filo vectors, figuring
   * out proper translation to byte vector length and alignment.
   * NOTE: We don't really use FlatBuffer's individual element read methods, so I suppose the
   * length in the FBB vector doesn't matter, but it's much better to be consistent to avoid bugs
   * @param nbits the # of bits per element
   * @param numElems the number of nbits length elements
   * @param alignment the byte alignment, eg 1 = byte aligned, 4 = int aligned
   *        This is basically what chunk size is going to fill up the FBB.
   *        Should be a power of two, I think.
   * @param addFunc a func to populate the elements in FBB, in reverse order
   * @return (offset, nbits)
   */
  def makeByteVector(fbb: FlatBufferBuilder, nbits: Int, numElems: Int, alignment: Int)
                    (addFunc: FlatBufferBuilder => Unit): (Int, Int) = {
    fbb.startVector(1, roundUp(nbits * numElems, alignment * 8) / 8, alignment)
    addFunc(fbb)
    (fbb.endVector(), nbits)
  }

  // Builds the FB [ubyte] vector for data of different types
  // They are all fed a reverse iterator of items
  def byteVect(fbb: FlatBufferBuilder, len: Int, reverseElems: Iterator[Byte]): (Int, Int) =
    makeByteVector(fbb, 8, len, 1) { fbb =>
      while (reverseElems.hasNext) { fbb.putByte(reverseElems.next) }
    }

  def shortVect(fbb: FlatBufferBuilder, len: Int, reverseElems: Iterator[Short]): (Int, Int) =
    makeByteVector(fbb, 16, len, 2) { fbb =>
      while (reverseElems.hasNext) { fbb.putShort(reverseElems.next) }
    }

  def intVect(fbb: FlatBufferBuilder, len: Int, reverseElems: Iterator[Int]): (Int, Int) =
    makeByteVector(fbb, 32, len, 4) { fbb =>
      while (reverseElems.hasNext) { fbb.putInt(reverseElems.next) }
    }

  def longVect(fbb: FlatBufferBuilder, len: Int, reverseElems: Iterator[Long]): (Int, Int) =
    makeByteVector(fbb, 64, len, 8) { fbb =>
      while (reverseElems.hasNext) { fbb.putLong(reverseElems.next) }
    }

  def doubleVect(fbb: FlatBufferBuilder, len: Int, reverseElems: Iterator[Double]): (Int, Int) =
    makeByteVector(fbb, 64, len, 8) { fbb =>
      while (reverseElems.hasNext) { fbb.putDouble(reverseElems.next) }
    }

  def floatVect(fbb: FlatBufferBuilder, len: Int, reverseElems: Iterator[Float]): (Int, Int) =
    makeByteVector(fbb, 32, len, 4) { fbb =>
      while (reverseElems.hasNext) { fbb.putFloat(reverseElems.next) }
    }

  // stringVect is fed a Seq of strings in normal order
  // Only the offset is returned, nbits is not useful for [string]
  def stringVect(fbb: FlatBufferBuilder, data: Seq[String]): Int = {
    // Create string vectors in reverse order, since FBB builds from top down
    // Also remember in FBB you cannot nest vector creation, so create all string vects first
    val reverseOffsets = data.reverseMap { str =>
      if (str != null) fbb.createString(str) else 0
    }
    fbb.startVector(4, data.length, 4)
    reverseOffsets.foreach(fbb.addOffset)
    fbb.endVector()
  }

  def putHeaderAndGet(fbb: FlatBufferBuilder, headerBytes: Int): ByteBuffer = {
    fbb.addInt(headerBytes)
    // Create a separate bytebuffer as original might be reused
    ByteBuffer.wrap(fbb.sizedByteArray).order(ByteOrder.LITTLE_ENDIAN)
  }

  def putHeaderAndGet(fbb: FlatBufferBuilder, majorVectorType: Int, subType: Int): ByteBuffer =
    putHeaderAndGet(fbb, WireFormat(majorVectorType, subType))
}

/**
 * Mix in to encoders to allow reuse of ByteBuffers for subsequent building of FBBs.
 * This avoids allocation and GC of lots of ByteBuffers during heavy periods of building Filo vectors.
 * Note that FBB itself can "grow" a BB by allocating a new bigger one, and that would not be saved here.
 * That's probably a good thing, because otherwise the BB for each thread could grow to be really big.
 */
trait ThreadLocalBuffers {
  val bb = new ThreadLocal[ByteBuffer]

  def getBuffer: ByteBuffer = {
    val _bb = bb.get
    if (_bb == null) {
      val newbb = ByteBuffer.allocate(Utils.BufferSize)
      bb.set(newbb)
      newbb
    } else {
      _bb
    }
  }
}
