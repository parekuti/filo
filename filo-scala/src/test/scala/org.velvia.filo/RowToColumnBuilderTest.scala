package org.velvia.filo

import org.scalatest.FunSpec
import org.scalatest.Matchers

class RowToVectorBuilderTest extends FunSpec with Matchers {
  val schema = Seq(
                 VectorInfo("name", classOf[String]),
                 VectorInfo("age",  classOf[Int])
               )

  val rows = Seq(
               (Some("Matthew Perry"), Some(18)),
               (Some("Michelle Pfeiffer"), None),
               (Some("George C"), Some(59)),
               (Some("Rich Sherman"), Some(26))
             )

  val utf8schema = Seq(
                 VectorInfo("name", classOf[ZeroCopyUTF8String]),
                 VectorInfo("age",  classOf[Int])
               )

  describe("RowToVectorBuilder") {
    import VectorReader._

    it("should add rows and convert them to Filo binary Seqs") {
      val rtcb = new RowToVectorBuilder(schema)
      rows.map(TupleRowReader).foreach(rtcb.addRow)
      rtcb.addEmptyRow()
      val columnData = rtcb.convertToBytes()

      columnData.keys should equal (Set("name", "age"))
      val nameBinSeq = FiloVector[String](columnData("name"))
      nameBinSeq.toList should equal (List("Matthew Perry", "Michelle Pfeiffer",
                                                 "George C", "Rich Sherman"))
      val ageBinSeq = FiloVector[Int](columnData("age"))
      ageBinSeq should have length (5)
      ageBinSeq(0) should equal (18)
      ageBinSeq.toList should equal (List(18, 59, 26))
    }

    it("should add UTF8 rows and convert to Filo binary seqs") {
      val rtcb = new RowToVectorBuilder(utf8schema)
      rows.map(TupleRowReader).foreach(rtcb.addRow)
      rtcb.addEmptyRow()
      val columnData = rtcb.convertToBytes()

      columnData.keys should equal (Set("name", "age"))
      val nameBinSeq = FiloVector[ZeroCopyUTF8String](columnData("name"))
      nameBinSeq.length should equal (rows.length + 1)
      nameBinSeq.map(_.toString) should equal (List("Matthew Perry", "Michelle Pfeiffer",
                                                 "George C", "Rich Sherman"))
    }

    it("convenience func should turn rows into bytes") {
      val columnData = RowToVectorBuilder.buildFromRows(rows.map(TupleRowReader).toIterator,
                                                        schema,
                                                        BuilderEncoder.SimpleEncoding)
      columnData.keys should equal (Set("name", "age"))
      val nameBinSeq = FiloVector[String](columnData("name"))
      nameBinSeq.toList should equal (List("Matthew Perry", "Michelle Pfeiffer",
                                                 "George C", "Rich Sherman"))
    }
  }
}