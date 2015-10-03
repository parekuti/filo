// automatically generated, do not modify

package org.velvia.filo.vector;

import java.nio.*;
import java.lang.*;
import java.util.*;
import com.google.flatbuffers.*;

@SuppressWarnings("unused")
public final class SimplePrimitiveVector extends Table {
  public static SimplePrimitiveVector getRootAsSimplePrimitiveVector(ByteBuffer _bb) { return getRootAsSimplePrimitiveVector(_bb, new SimplePrimitiveVector()); }
  public static SimplePrimitiveVector getRootAsSimplePrimitiveVector(ByteBuffer _bb, SimplePrimitiveVector obj) { _bb.order(ByteOrder.LITTLE_ENDIAN); return (obj.__init(_bb.getInt(_bb.position()) + _bb.position(), _bb)); }
  public SimplePrimitiveVector __init(int _i, ByteBuffer _bb) { bb_pos = _i; bb = _bb; return this; }

  public int len() { int o = __offset(4); return o != 0 ? bb.getInt(o + bb_pos) : 0; }
  public NaMask naMask() { return naMask(new NaMask()); }
  public NaMask naMask(NaMask obj) { int o = __offset(6); return o != 0 ? obj.__init(__indirect(o + bb_pos), bb) : null; }
  public int nbits() { int o = __offset(8); return o != 0 ? bb.get(o + bb_pos) & 0xFF : 0; }
  public int data(int j) { int o = __offset(10); return o != 0 ? bb.get(__vector(o) + j * 1) & 0xFF : 0; }
  public int dataLength() { int o = __offset(10); return o != 0 ? __vector_len(o) : 0; }
  public ByteBuffer dataAsByteBuffer() { return __vector_as_bytebuffer(10, 1); }

  public static int createSimplePrimitiveVector(FlatBufferBuilder builder,
      int len,
      int naMask,
      int nbits,
      int data) {
    builder.startObject(4);
    SimplePrimitiveVector.addData(builder, data);
    SimplePrimitiveVector.addNaMask(builder, naMask);
    SimplePrimitiveVector.addLen(builder, len);
    SimplePrimitiveVector.addNbits(builder, nbits);
    return SimplePrimitiveVector.endSimplePrimitiveVector(builder);
  }

  public static void startSimplePrimitiveVector(FlatBufferBuilder builder) { builder.startObject(4); }
  public static void addLen(FlatBufferBuilder builder, int len) { builder.addInt(0, len, 0); }
  public static void addNaMask(FlatBufferBuilder builder, int naMaskOffset) { builder.addOffset(1, naMaskOffset, 0); }
  public static void addNbits(FlatBufferBuilder builder, int nbits) { builder.addByte(2, (byte)(nbits & 0xFF), 0); }
  public static void addData(FlatBufferBuilder builder, int dataOffset) { builder.addOffset(3, dataOffset, 0); }
  public static int createDataVector(FlatBufferBuilder builder, byte[] data) { builder.startVector(1, data.length, 1); for (int i = data.length - 1; i >= 0; i--) builder.addByte(data[i]); return builder.endVector(); }
  public static void startDataVector(FlatBufferBuilder builder, int numElems) { builder.startVector(1, numElems, 1); }
  public static int endSimplePrimitiveVector(FlatBufferBuilder builder) {
    int o = builder.endObject();
    return o;
  }
};
