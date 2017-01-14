package org.velvia.filo

import java.nio.ByteBuffer
import java.sql.Timestamp
import org.joda.time.DateTime
import scala.reflect.ClassTag

/**
 * A generic trait for reading typed values out of a row of data.
 * Used for both reading out of Filo vectors as well as for RowToVectorBuilder,
 * which means it can be used to compose heterogeneous Filo vectors together.
 */
trait RowReader {
  def notNull(columnNo: Int): Boolean
  def getBoolean(columnNo: Int): Boolean
  def getInt(columnNo: Int): Int
  def getLong(columnNo: Int): Long
  def getDouble(columnNo: Int): Double
  def getFloat(columnNo: Int): Float
  def getString(columnNo: Int): String
  def getAny(columnNo: Int): Any

  // Please override final def if your RowReader has a faster implementation
  def filoUTF8String(columnNo: Int): ZeroCopyUTF8String =
    Option(getString(columnNo)).map(ZeroCopyUTF8String.apply).getOrElse(ZeroCopyUTF8String.empty)

  /**
   * This method serves two purposes.
   * For RowReaders that need to parse from some input source, such as CSV,
   * the ClassTag gives a way for per-type parsing for non-primitive types.
   * For RowReaders for fast reading paths, such as Spark, the default
   * implementation serves as a fast way to read from objects.
   */
  def as[T: ClassTag](columnNo: Int): T = getAny(columnNo).asInstanceOf[T]
}

/**
 * An example of a RowReader that can read from Scala tuples containing Option[_]
 */
case class TupleRowReader(tuple: Product) extends RowReader {
  def notNull(columnNo: Int): Boolean =
    tuple.productElement(columnNo).asInstanceOf[Option[Any]].nonEmpty

  def getBoolean(columnNo: Int): Boolean = tuple.productElement(columnNo) match {
    case Some(x: Boolean) => x
    case None => false
  }

  def getInt(columnNo: Int): Int = tuple.productElement(columnNo) match {
    case Some(x: Int) => x
    case None => 0
  }

  def getLong(columnNo: Int): Long = tuple.productElement(columnNo) match {
    case Some(x: Long) => x
    case Some(x: Timestamp) => x.getTime
    case None => 0L
  }

  def getDouble(columnNo: Int): Double = tuple.productElement(columnNo) match {
    case Some(x: Double) => x
    case None => 0.0
  }

  def getFloat(columnNo: Int): Float = tuple.productElement(columnNo) match {
    case Some(x: Float) => x
    case None => 0.0F
  }

  def getString(columnNo: Int): String = tuple.productElement(columnNo) match {
    case Some(x: String) => x
    case None => null
  }

  def getAny(columnNo: Int): Any =
    tuple.productElement(columnNo).asInstanceOf[Option[Any]].getOrElse(null)
}

/**
 * A RowReader for working with OpenCSV or anything else that emits string[]
 */
case class ArrayStringRowReader(strings: Array[String]) extends RowReader {
  //scalastyle:off
  def notNull(columnNo: Int): Boolean = strings(columnNo) != null && strings(columnNo) != ""
  //scalastyle:on
  def getBoolean(columnNo: Int): Boolean = strings(columnNo).toBoolean
  def getInt(columnNo: Int): Int = strings(columnNo).toInt
  def getLong(columnNo: Int): Long = try {
    strings(columnNo).toLong
  } catch {
    case ex: NumberFormatException => DateTime.parse(strings(columnNo)).getMillis
  }
  def getDouble(columnNo: Int): Double = strings(columnNo).toDouble
  def getFloat(columnNo: Int): Float = strings(columnNo).toFloat
  def getString(columnNo: Int): String = strings(columnNo)
  def getAny(columnNo: Int): Any = strings(columnNo)

  override def as[T: ClassTag](columnNo: Int): T = {
    (implicitly[ClassTag[T]].runtimeClass match {
      case Classes.DateTime => new DateTime(strings(columnNo))
      case Classes.SqlTimestamp => new Timestamp(DateTime.parse(strings(columnNo)).getMillis)
    }).asInstanceOf[T]
  }
}

/**
 * A RowReader that changes the column numbers around of an original RowReader.  It could be used to
 * present a subset of the original columns, for example.
 * @param columnRoutes an array of original column numbers for the column in question.  For example:
 *                     Array(0, 2, 5) means an getInt(1) would map to a getInt(2) for the original RowReader
 */
case class RoutingRowReader(origReader: RowReader, columnRoutes: Array[Int]) extends RowReader {
  def notNull(columnNo: Int): Boolean    = origReader.notNull(columnRoutes(columnNo))
  def getBoolean(columnNo: Int): Boolean = origReader.getBoolean(columnRoutes(columnNo))
  def getInt(columnNo: Int): Int         = origReader.getInt(columnRoutes(columnNo))
  def getLong(columnNo: Int): Long       = origReader.getLong(columnRoutes(columnNo))
  def getDouble(columnNo: Int): Double   = origReader.getDouble(columnRoutes(columnNo))
  def getFloat(columnNo: Int): Float     = origReader.getFloat(columnRoutes(columnNo))
  def getString(columnNo: Int): String   = origReader.getString(columnRoutes(columnNo))
  def getAny(columnNo: Int): Any         = origReader.getAny(columnRoutes(columnNo))

  override def equals(other: Any): Boolean = other match {
    case RoutingRowReader(orig, _) => orig.equals(origReader)
    case r: RowReader              => r.equals(origReader)
    case other: Any                => false
  }
}

case class SingleValueRowReader(value: Any) extends RowReader {
  def notNull(columnNo: Int): Boolean = Option(value).isDefined
  def getBoolean(columnNo: Int): Boolean = value.asInstanceOf[Boolean]
  def getInt(columnNo: Int): Int = value.asInstanceOf[Int]
  def getLong(columnNo: Int): Long = value.asInstanceOf[Long]
  def getDouble(columnNo: Int): Double = value.asInstanceOf[Double]
  def getFloat(columnNo: Int): Float = value.asInstanceOf[Float]
  def getString(columnNo: Int): String = value.asInstanceOf[String]
  def getAny(columnNo: Int): Any = value
}

case class SeqRowReader(sequence: Seq[Any]) extends RowReader {
  def notNull(columnNo: Int): Boolean = true
  def getBoolean(columnNo: Int): Boolean = sequence(columnNo).asInstanceOf[Boolean]
  def getInt(columnNo: Int): Int = sequence(columnNo).asInstanceOf[Int]
  def getLong(columnNo: Int): Long = sequence(columnNo).asInstanceOf[Long]
  def getDouble(columnNo: Int): Double = sequence(columnNo).asInstanceOf[Double]
  def getFloat(columnNo: Int): Float = sequence(columnNo).asInstanceOf[Float]
  def getString(columnNo: Int): String = sequence(columnNo).asInstanceOf[String]
  def getAny(columnNo: Int): Any = sequence(columnNo)
}

object RowReader {
  import DefaultValues._

  // Type class for extracting a field of a specific type .. and comparing a field from two RowReaders
  trait TypedFieldExtractor[@specialized F] {
    def getField(reader: RowReader, columnNo: Int): F
    def getFieldOrDefault(reader: RowReader, columnNo: Int): F = getField(reader, columnNo)
    def compare(reader: RowReader, other: RowReader, columnNo: Int): Int
  }

  class WrappedExtractor[@specialized T, F: TypedFieldExtractor](func: F => T)
    extends TypedFieldExtractor[T] {
    val orig = implicitly[TypedFieldExtractor[F]]
    def getField(reader: RowReader, columnNo: Int): T = func(orig.getField(reader, columnNo))
    def compare(reader: RowReader, other: RowReader, col: Int): Int = orig.compare(reader, other, col)
  }

  implicit object BooleanFieldExtractor extends TypedFieldExtractor[Boolean] {
    final def getField(reader: RowReader, columnNo: Int): Boolean = reader.getBoolean(columnNo)
    final def compare(reader: RowReader, other: RowReader, columnNo: Int): Int =
      java.lang.Boolean.compare(getFieldOrDefault(reader, columnNo), getFieldOrDefault(other, columnNo))
  }

  implicit object LongFieldExtractor extends TypedFieldExtractor[Long] {
    final def getField(reader: RowReader, columnNo: Int): Long = reader.getLong(columnNo)
    final def compare(reader: RowReader, other: RowReader, columnNo: Int): Int =
      java.lang.Long.compare(getFieldOrDefault(reader, columnNo), getFieldOrDefault(other, columnNo))
  }

  implicit object IntFieldExtractor extends TypedFieldExtractor[Int] {
    final def getField(reader: RowReader, columnNo: Int): Int = reader.getInt(columnNo)
    final def compare(reader: RowReader, other: RowReader, columnNo: Int): Int =
      java.lang.Integer.compare(getFieldOrDefault(reader, columnNo), getFieldOrDefault(other, columnNo))
  }

  implicit object DoubleFieldExtractor extends TypedFieldExtractor[Double] {
    final def getField(reader: RowReader, columnNo: Int): Double = reader.getDouble(columnNo)
    final def compare(reader: RowReader, other: RowReader, columnNo: Int): Int =
      java.lang.Double.compare(getFieldOrDefault(reader, columnNo), getFieldOrDefault(other, columnNo))
  }

  implicit object FloatFieldExtractor extends TypedFieldExtractor[Float] {
    final def getField(reader: RowReader, columnNo: Int): Float = reader.getFloat(columnNo)
    final def compare(reader: RowReader, other: RowReader, columnNo: Int): Int =
      java.lang.Float.compare(getFieldOrDefault(reader, columnNo), getFieldOrDefault(other, columnNo))
  }

  implicit object StringFieldExtractor extends TypedFieldExtractor[String] {
    final def getField(reader: RowReader, columnNo: Int): String = reader.getString(columnNo)
    override final def getFieldOrDefault(reader: RowReader, columnNo: Int): String = {
      val str = reader.getString(columnNo)
      if (str == null) DefaultString else str
    }
    final def compare(reader: RowReader, other: RowReader, columnNo: Int): Int =
      getFieldOrDefault(reader, columnNo).compareTo(getFieldOrDefault(other, columnNo))
  }

  implicit object UTF8StringFieldExtractor extends TypedFieldExtractor[ZeroCopyUTF8String] {
    final def getField(reader: RowReader, columnNo: Int): ZeroCopyUTF8String =
      reader.filoUTF8String(columnNo)
    // TODO: do UTF8 comparison so we can avoid having to deserialize
    final def compare(reader: RowReader, other: RowReader, columnNo: Int): Int =
      getFieldOrDefault(reader, columnNo).compareTo(getFieldOrDefault(other, columnNo))
  }

  implicit object DateTimeFieldExtractor extends TypedFieldExtractor[DateTime] {
    final def getField(reader: RowReader, columnNo: Int): DateTime = reader.as[DateTime](columnNo)
    override final def getFieldOrDefault(reader: RowReader, columnNo: Int): DateTime = {
      val dt = reader.as[DateTime](columnNo)
      if (dt == null) DefaultDateTime else dt
    }
    final def compare(reader: RowReader, other: RowReader, columnNo: Int): Int =
      getFieldOrDefault(reader, columnNo).compareTo(getFieldOrDefault(other, columnNo))
  }

  implicit object TimestampFieldExtractor extends TypedFieldExtractor[Timestamp] {
    final def getField(reader: RowReader, columnNo: Int): Timestamp = reader.as[Timestamp](columnNo)
    override final def getFieldOrDefault(reader: RowReader, columnNo: Int): Timestamp = {
      val ts = reader.as[Timestamp](columnNo)
      if (ts == null) DefaultTimestamp else ts
    }
    // TODO: compare the Long, instead of deserializing and comparing Timestamp object
    final def compare(reader: RowReader, other: RowReader, columnNo: Int): Int =
      getFieldOrDefault(reader, columnNo).compareTo(getFieldOrDefault(other, columnNo))
  }
}
