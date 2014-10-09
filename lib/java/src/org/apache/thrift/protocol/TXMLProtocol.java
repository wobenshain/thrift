/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.thrift.protocol;

import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.util.Stack;

import org.apache.thrift.TByteArrayOutputStream;
import org.apache.thrift.TException;
import org.apache.thrift.transport.TTransport;

public class TXMLProtocol extends TProtocol {
  public static class Factory implements TProtocolFactory {

    public TProtocol getProtocol(TTransport trans) {
      return new TXMLProtocol(trans);
    }

  }
  
  private static class ThreeTuple<F,S,T> {
	  private final F first;
	  private final S second;
	  private final T third;
	  public ThreeTuple(F first, S second, T third) {
		  this.first = first;
		  this.second = second;
		  this.third = third;
	  }
	  public F getFirst() { return first; }
	  public S getSecond() { return second; }
	  public T getThird() { return third; }
  }

  private static final byte[] AMPERSAND = new byte[] { '&' };
  private static final byte[] LANGLE = new byte[] {'<'};
  private static final byte[] RANGLE = new byte[] {'>'};
  private static final byte[] QUOTE = new byte[] {'"'};
  private static final byte[] SLASH = new byte[] {'/'};
  private static final byte[] SPACE = new byte[] {' '};

  private static final byte[] AMPERESC = new byte[] {'&','a','m','p',';'};
  private static final byte[] ENTRYCOUNT = new byte[] {'e','n','t','r','y','_','c','o','u','n','t'};
  private static final byte[] FIELDEQUALS = new byte[] {'f','i','e','l','d','='};
  private static final byte[] KEY = new byte[] {'k','e','y'};
  private static final byte[] KEYTYPE = new byte[] {'k','e','y','_','t','y','p','e'};
  private static final byte[] LANGESC = new byte[] {'&','l','t',';'};
  private static final byte[] TYPEEQUALS = new byte[] {'t','y','p','e','='};
  private static final byte[] VALUE = new byte[] {'v','a','l'};
  private static final byte[] VALUETYPE = new byte[] {'v','a','l','_','t','y','p','e'};
  
  private static final long  VERSION = 1;

  private static final byte[] NAME_BOOL = new byte[] {'b','o','o','l','e','a','n'};
  private static final byte[] NAME_BYTE = new byte[] {'b','y','t','e'};
  private static final byte[] NAME_I16 = new byte[] {'s','h','o','r','t'};
  private static final byte[] NAME_I32 = new byte[] {'i','n','t'};
  private static final byte[] NAME_I64 = new byte[] {'l','o','n','g'};
  private static final byte[] NAME_DOUBLE = new byte[] {'d','e','c','i','m','a','l'};
  private static final byte[] NAME_STRUCT = new byte[] {'r','e','c'};
  private static final byte[] NAME_STRING = new byte[] {'s','t','r','i','n','g'};
  private static final byte[] NAME_MAP = new byte[] {'m','a','p'};
  private static final byte[] NAME_LIST = new byte[] {'l','i','s','t'};
  private static final byte[] NAME_SET = new byte[] {'s','e','t'};

  private static final TStruct ANONYMOUS_STRUCT = new TStruct();

  private static final byte[] getTypeNameForTypeID(byte typeID)
    throws TException {
    switch (typeID) {
    case TType.BOOL:
      return NAME_BOOL;
    case TType.BYTE:
      return NAME_BYTE;
    case TType.I16:
      return NAME_I16;
    case TType.I32:
      return NAME_I32;
    case TType.I64:
      return NAME_I64;
    case TType.DOUBLE:
      return NAME_DOUBLE;
    case TType.STRING:
      return NAME_STRING;
    case TType.STRUCT:
      return NAME_STRUCT;
    case TType.MAP:
      return NAME_MAP;
    case TType.SET:
      return NAME_SET;
    case TType.LIST:
      return NAME_LIST;
    default:
      throw new TProtocolException(TProtocolException.NOT_IMPLEMENTED, "Unrecognized type");
    }
  }

  private static final byte getTypeIDForTypeName(byte[] name)
    throws TException {
    byte result = TType.STOP;
    if (name.length > 1) {
      switch (name[0]) {
      case 'b':
        switch (name[1]) {
        case 'o':
          if (new String(name).equals(new String(NAME_BOOL)))
        	result = TType.BOOL;
          break;
        case 'y':
          if (new String(name).equals(new String(NAME_BYTE)))
            result = TType.BYTE;
          break;
        }
        break;
      case 'd':
    	if (new String(name).equals(new String(NAME_DOUBLE)))
    	  result = TType.DOUBLE;
        break;
      case 'i':
      	if (new String(name).equals(new String(NAME_I32)))
    	  result = TType.I32;
        break;
      case 'l':
    	switch (name[1]) {
    	case 'i':
          if (new String(name).equals(new String(NAME_LIST)))
            result = TType.LIST;
          break;
    	case 'o':
          if (new String(name).equals(new String(NAME_I64)))
            result = TType.I64;
          break;
    	}
    	break;
      case 'm':
        if (new String(name).equals(new String(NAME_MAP)))
          result = TType.MAP;
        break;
      case 's':
        switch (name[1]) {
        case 'e':
          if (new String(name).equals(new String(NAME_SET)))
            result = TType.SET;
          break;
        case 'h':
          if (new String(name).equals(new String(NAME_I16)))
            result = TType.I16;
          break;
        case 't':
          if (new String(name).equals(new String(NAME_STRING)))
            result = TType.STRING;
          break;
        }
        break;
      }
    }
    if (result == TType.STOP) {
      throw new TProtocolException(TProtocolException.NOT_IMPLEMENTED, "Unrecognized type");
    }
    return result;
  }

  // Base class for tracking JSON contexts that may require inserting/reading
  // additional JSON syntax characters
  // This base context does nothing.
  protected class XMLBaseContext {
    protected void write() throws TException {}

    protected void read() throws TException {}
  }

  protected class XMLPairContext extends XMLBaseContext {
    private boolean open_ = true;
    private final String fieldName;
    private final Byte fieldType;
    private final Short fieldId;

    protected XMLPairContext() throws TException {
  	  open_ = false;
  	  ThreeTuple<String,Byte,Short> returns = readXMLTag();
  	  this.fieldName = returns.getFirst();
  	  this.fieldType = returns.getSecond();
  	  this.fieldId = returns.getThird();
    }
    
    protected XMLPairContext(String fieldName) {
      this(fieldName, null, null);
    }
    
    protected XMLPairContext(String name, Byte type, Short id) {
      this.fieldName = name;
      this.fieldType = type;
      this.fieldId = id;
    }
    
    @Override
    protected void write() throws TException {
      trans_.write(LANGLE);
      if (!open_) {
        trans_.write(SLASH);
      }
      trans_.write(fieldName.getBytes());
      if (open_) {
        if (fieldType != null) {
          trans_.write(SPACE);
          trans_.write(TYPEEQUALS);
          trans_.write(QUOTE);
          trans_.write(getTypeNameForTypeID(fieldType));
          trans_.write(QUOTE);
        }
        if (fieldId != null) {
          trans_.write(SPACE);
          trans_.write(FIELDEQUALS);
          trans_.write(QUOTE);
          trans_.write(Short.toString(fieldId).getBytes());
          trans_.write(QUOTE);
        }
      }
      open_ = !open_;
      trans_.write(RANGLE);
    }

    @Override
    protected void read() throws TException {
      readXMLSyntaxChar(LANGLE);
      readXMLSyntaxChar(SLASH);
      readXMLSyntaxChar(fieldName.getBytes());
      readXMLSyntaxChar(RANGLE);
    }

    protected String getName() { return fieldName; }
    protected Byte getType() { return fieldType; }
  }

  protected class XMLMapContext extends XMLBaseContext {
    private byte Stage = 0;
    private boolean first_ = true;
    private final Byte keyType;
    private final Byte valueType;
    private final Integer fieldCount;

    public XMLMapContext(Byte keyType, Byte valueType, Integer fieldCount) {
      this.keyType = keyType;
      this.valueType = valueType;
      this.fieldCount = fieldCount;
	}

	@Override
    protected void write() throws TException {
      if (first_) {
        first_ = false;
        trans_.write(LANGLE);
        trans_.write(KEYTYPE);
        trans_.write(RANGLE);
        trans_.write(getTypeNameForTypeID(keyType));
        trans_.write(LANGLE);
        trans_.write(SLASH);
        trans_.write(KEYTYPE);
        trans_.write(RANGLE);
        trans_.write(LANGLE);
        trans_.write(VALUETYPE);
        trans_.write(RANGLE);
        trans_.write(getTypeNameForTypeID(valueType));
        trans_.write(LANGLE);
        trans_.write(SLASH);
        trans_.write(VALUETYPE);
        trans_.write(RANGLE);
        trans_.write(LANGLE);
        trans_.write(ENTRYCOUNT);
        trans_.write(RANGLE);
        trans_.write(Integer.toString(fieldCount).getBytes());
        trans_.write(LANGLE);
        trans_.write(SLASH);
        trans_.write(ENTRYCOUNT);
        trans_.write(RANGLE);
      }
      if (Stage == 0) {
    	  Stage = 1;
    	  trans_.write(LANGLE);
    	  trans_.write(KEY);
    	  trans_.write(RANGLE);
      } else if (Stage == 1) {
    	  trans_.write(LANGLE);
    	  trans_.write(SLASH);
    	  trans_.write(KEY);
    	  trans_.write(RANGLE);
    	  Stage = 2;
      } else if (Stage == 2) {
    	  trans_.write(LANGLE);
    	  trans_.write(VALUE);
    	  trans_.write(RANGLE);
    	  Stage = 3;
      } else if (Stage == 3) {
    	  trans_.write(LANGLE);
    	  trans_.write(SLASH);
    	  trans_.write(VALUE);
    	  trans_.write(RANGLE);
    	  Stage=0;
      }
    }

    @Override
    protected void read() throws TException {
      if (Stage == 0) {
        readXMLSyntaxChar(LANGLE);
        readXMLSyntaxChar(KEY);
        readXMLSyntaxChar(RANGLE);
        Stage = 1;
      } else if (Stage == 1) {
        readXMLSyntaxChar(LANGLE);
        readXMLSyntaxChar(SLASH);
        readXMLSyntaxChar(KEY);
        readXMLSyntaxChar(RANGLE);
        Stage = 2;
      } else if (Stage == 2) {
        readXMLSyntaxChar(LANGLE);
        readXMLSyntaxChar(VALUE);
        readXMLSyntaxChar(RANGLE);
        Stage = 3;
      } else if (Stage == 3) {
        readXMLSyntaxChar(LANGLE);
        readXMLSyntaxChar(SLASH);
        readXMLSyntaxChar(VALUE);
        readXMLSyntaxChar(RANGLE);
        Stage = 0;
      }
    }
  }

  protected class XMLListContext extends XMLBaseContext {
    private boolean first_ = true;
    private boolean open_ = true;
    private final Byte type;
    private final Integer fieldCount;

    protected XMLListContext(Byte type, Integer fieldCount) {
      this.type = type;
      this.fieldCount = fieldCount;
    }

    @Override
    protected void write() throws TException {
      if (first_) {
        first_ = false;
        trans_.write(LANGLE);
        trans_.write(VALUETYPE);
        trans_.write(RANGLE);
        trans_.write(getTypeNameForTypeID(type));
        trans_.write(LANGLE);
        trans_.write(SLASH);
        trans_.write(VALUETYPE);
        trans_.write(RANGLE);
        trans_.write(LANGLE);
        trans_.write(ENTRYCOUNT);
        trans_.write(RANGLE);
        trans_.write(Integer.toString(fieldCount).getBytes());
        trans_.write(LANGLE);
        trans_.write(SLASH);
        trans_.write(ENTRYCOUNT);
        trans_.write(RANGLE);
      }
      trans_.write(LANGLE);
      if (!open_)
        trans_.write(SLASH);
      trans_.write(VALUE);
      trans_.write(RANGLE);
      open_ = !open_;
    }

    @Override
    protected void read() throws TException {
      readXMLSyntaxChar(LANGLE);
      if (!open_)
        readXMLSyntaxChar(SLASH);
      readXMLSyntaxChar(VALUE);
      readXMLSyntaxChar(RANGLE);
      open_ = !open_;
    }
  }

  protected class LookaheadReader {

    private boolean hasData_;
    private byte[] data_ = new byte[1];

    // Return and consume the next byte to be read, either taking it from the
    // data buffer if present or getting it from the transport otherwise.
    protected byte read() throws TException {
      if (hasData_) {
        hasData_ = false;
      }
      else {
        trans_.readAll(data_, 0, 1);
      }
      return data_[0];
    }

    // Return the next byte to be read without consuming, filling the data
    // buffer if it has not been filled already.
    protected byte peek() throws TException {
      if (!hasData_) {
        trans_.readAll(data_, 0, 1);
      }
      hasData_ = true;
      return data_[0];
    }
  }

  // Stack of nested contexts that we may be in
  private Stack<XMLBaseContext> contextStack_ = new Stack<XMLBaseContext>();

  // Current context that we are in
  private XMLBaseContext context_ = new XMLBaseContext();

  // Reader that manages a 1-byte buffer
  private LookaheadReader reader_ = new LookaheadReader();

  // Push a new JSON context onto the stack.
  private void pushContext(XMLBaseContext c) {
    contextStack_.push(context_);
    context_ = c;
  }

  // Pop the last JSON context off the stack
  private void popContext() {
    context_ = contextStack_.pop();
  }

  /**
   * Constructor
   */
  public TXMLProtocol(TTransport trans) {
    super(trans);
  }

  @Override
  public void reset() {
    contextStack_.clear();
    context_ = new XMLBaseContext();
    reader_ = new LookaheadReader();
  }

  // Temporary buffer used by several methods
  private byte[] tmpbuf_ = new byte[4];

  // Read a byte that must match b[0]; otherwise an exception is thrown.
  protected void readXMLSyntaxChar(byte[] b) throws TException {
    for (int i = 0; i < b.length; i++) {
      byte ch = reader_.read();
      if (ch != b[i]) {
        throw new TProtocolException(TProtocolException.INVALID_DATA, "Unexpected character:" + (char)ch);
      }
    }
  }

  private void writeXMLString(byte[] b) throws TException {
    int len = b.length;
    for (int i = 0; i < len; i++) {
      if (b[i] == AMPERSAND[0]) {
        trans_.write(AMPERESC);
      } else if (b[i] == LANGLE[0]) {
        trans_.write(LANGESC);
      } else {
        trans_.write(b, i, 1);
      }
    }
  }

  private void writeXMLInteger(long num) throws TException {
    String str = Long.toString(num);
    try {
      byte[] buf = str.getBytes("UTF-8");
      trans_.write(buf);
    } catch (UnsupportedEncodingException uex) {
      throw new TException("JVM DOES NOT SUPPORT UTF-8");
    }
  }

  // Write out a double as a JSON value. If it is NaN or infinity or if the
  // context dictates escaping, write out as JSON string.
  private void writeXMLDouble(double num) throws TException {
    String str = Double.toString(num);
    try {
      byte[] b = str.getBytes("UTF-8");
      trans_.write(b, 0, b.length);
    } catch (UnsupportedEncodingException uex) {
      throw new TException("JVM DOES NOT SUPPORT UTF-8");
    }
  }

  // Write out contents of byte array b as a JSON string with base-64 encoded
  // data
  private void writeXMLBase64(byte[] b, int offset, int length) throws TException {
    int len = length;
    int off = offset;
    while (len >= 3) {
      // Encode 3 bytes at a time
      TBase64Utils.encode(b, off, 3, tmpbuf_, 0);
      trans_.write(tmpbuf_, 0, 4);
      off += 3;
      len -= 3;
    }
    if (len > 0) {
      // Encode remainder
      TBase64Utils.encode(b, off, len, tmpbuf_, 0);
      trans_.write(tmpbuf_, 0, len + 1);
    }
  }

  private void writeXMLStart(String fieldName) throws TException {
	  pushContext(new XMLPairContext(fieldName));
  }
  
  private void writeXMLStart(String fieldName, byte type, short id) throws TException {
	  pushContext(new XMLPairContext(fieldName, type, id));
  }
  
  private void writeXMLEnd() throws TException {
	  if (contextStack_.size() > 0) {
		  popContext();
	  }
  }

  @Override
  public void writeMessageBegin(TMessage message) throws TException {
//    writeJSONArrayStart();
    writeXMLInteger(VERSION);
    try {
      byte[] b = message.name.getBytes("UTF-8");
      writeXMLString(b);
    } catch (UnsupportedEncodingException uex) {
      throw new TException("JVM DOES NOT SUPPORT UTF-8");
    }
    writeXMLInteger(message.type);
    writeXMLInteger(message.seqid);
  }

  @Override
  public void writeMessageEnd() throws TException {
//    writeJSONArrayEnd();
  }

  @Override
  public void writeStructBegin(TStruct struct) throws TException {
    writeXMLStart(struct.name);
    context_.write();
  }

  @Override
  public void writeStructEnd() throws TException {
    context_.write();
    writeXMLEnd();
  }

  @Override
  public void writeFieldBegin(TField field) throws TException {
    writeXMLStart(field.name, field.type, field.id);
  }

  @Override
  public void writeFieldEnd() throws TException {
    writeXMLEnd();
  }

  @Override
  public void writeFieldStop() {}

  @Override
  public void writeMapBegin(TMap map) throws TException {
    context_.write();
    pushContext(new XMLMapContext(map.keyType,map.valueType,map.size));
  }

  @Override
  public void writeMapEnd() throws TException {
    popContext();
    context_.write();
  }

  @Override
  public void writeListBegin(TList list) throws TException {
	  context_.write();
	  pushContext(new XMLListContext(list.elemType,list.size));
  }

  @Override
  public void writeListEnd() throws TException {
	  popContext();
	  context_.write();
  }

  @Override
  public void writeSetBegin(TSet set) throws TException {
	  context_.write();
	  pushContext(new XMLListContext(set.elemType,set.size));
  }

  @Override
  public void writeSetEnd() throws TException {
	  popContext();
	  context_.write();
  }

  @Override
  public void writeBool(boolean b) throws TException {
    context_.write();
    writeXMLInteger(b ? (long)1 : (long)0);
    context_.write();
  }

  @Override
  public void writeByte(byte b) throws TException {
    context_.write();
    writeXMLInteger((long)b);
    context_.write();
  }

  @Override
  public void writeI16(short i16) throws TException {
    context_.write();
    writeXMLInteger((long)i16);
    context_.write();
  }

  @Override
  public void writeI32(int i32) throws TException {
    context_.write();
    writeXMLInteger((long)i32);
    context_.write();
  }

  @Override
  public void writeI64(long i64) throws TException {
    context_.write();
    writeXMLInteger(i64);
    context_.write();
  }

  @Override
  public void writeDouble(double dub) throws TException {
    context_.write();
    writeXMLDouble(dub);
    context_.write();
  }

  @Override
  public void writeString(String str) throws TException {
    context_.write();
    try {
      byte[] b = str.getBytes("UTF-8");
      writeXMLString(b);
    } catch (UnsupportedEncodingException uex) {
      throw new TException("JVM DOES NOT SUPPORT UTF-8");
    }
    context_.write();
  }

  @Override
  public void writeBinary(ByteBuffer bin) throws TException {
    context_.write();
    writeXMLBase64(bin.array(), bin.position() + bin.arrayOffset(), bin.limit() - bin.position() - bin.arrayOffset());
    context_.write();
  }

  /**
   * Reading methods.
   */

  private TByteArrayOutputStream readXMLString() throws TException {
    context_.read();    
	TByteArrayOutputStream fieldName = new TByteArrayOutputStream();
    while (true) {
      byte ch = reader_.peek();
      if (ch == LANGLE[0]) {
        break;
      }
      reader_.read();
      if (ch == AMPERSAND[0]) {
        boolean escaped = false;
        ch = reader_.read();
        if (ch == AMPERESC[1]) {
          ch = reader_.read();
          if (ch == AMPERESC[2]) {
            ch = reader_.read();
            if (ch == AMPERESC[3]) {
              ch = reader_.read();
              if (ch == AMPERESC[4]) {
                escaped = true;
                ch = '&';
              }
            }
          }
        } else if (ch == LANGESC[1]) {
          ch = reader_.read();
          if (ch == LANGESC[2]) {
            ch = reader_.read();
            if (ch == LANGESC[3]) {
              escaped = true;
              ch = '<';
            }
          }
        }
        if (!escaped) {
          throw new TProtocolException(TProtocolException.INVALID_DATA, "Expected escape char");
        }
      }
      fieldName.write(ch);
    }
    context_.read();
    return fieldName;
  }

private ThreeTuple<String,Byte,Short> readXMLTag() throws TException {
	boolean getType = false;
    readXMLSyntaxChar(LANGLE);
	TByteArrayOutputStream fieldName = new TByteArrayOutputStream();
    while (true) {
      byte ch = reader_.read();
      if (ch == SPACE[0]) {
        getType = true;
        break;
      } else if (ch == RANGLE[0]) {
        break;
      } else if (ch == AMPERSAND[0]) {
        boolean escaped = false;
        ch = reader_.read();
        if (ch == AMPERESC[1]) {
          ch = reader_.read();
          if (ch == AMPERESC[2]) {
            ch = reader_.read();
            if (ch == AMPERESC[3]) {
              ch = reader_.read();
              if (ch == AMPERESC[4]) {
                escaped = true;
                ch = '&';
              }
            }
          }
        } else if (ch == LANGESC[1]) {
          ch = reader_.read();
          if (ch == LANGESC[2]) {
            ch = reader_.read();
            if (ch == LANGESC[3]) {
              escaped = true;
              ch = '<';
            }
          }
        }
        if (!escaped) {
          throw new TProtocolException(TProtocolException.INVALID_DATA, "Expected escape char");
        }
      } else if (ch == LANGLE[0]) {
        throw new TProtocolException(TProtocolException.INVALID_DATA, "Expected escape char");
      }
      fieldName.write(ch);
    }
    TByteArrayOutputStream fieldType = new TByteArrayOutputStream();
    Short fieldId = null;
    if (getType) {
      readXMLSyntaxChar(TYPEEQUALS);
      readXMLSyntaxChar(QUOTE);
      while (true) {
        byte ch = reader_.read();
        if (ch == QUOTE[0]) {
          break;
        } else if (ch == AMPERSAND[0]) {
          boolean escaped = false;
          ch = reader_.read();
          if (ch == AMPERESC[1]) {
            ch = reader_.read();
            if (ch == AMPERESC[2]) {
              ch = reader_.read();
              if (ch == AMPERESC[3]) {
                ch = reader_.read();
                if (ch == AMPERESC[4]) {
                  escaped = true;
                  ch = '&';
                }
              }
            }
          } else if (ch == LANGESC[1]) {
            ch = reader_.read();
            if (ch == LANGESC[2]) {
              ch = reader_.read();
              if (ch == LANGESC[3]) {
                escaped = true;
                ch = '<';
              }
            }
          }
          if (!escaped) {
            throw new TProtocolException(TProtocolException.INVALID_DATA, "Expected escape char");
          }
        } else if (ch == LANGLE[0]) {
          throw new TProtocolException(TProtocolException.INVALID_DATA, "Expected escape char");
        }
        fieldType.write(ch);
      }
      readXMLSyntaxChar(SPACE);
      readXMLSyntaxChar(FIELDEQUALS);
      readXMLSyntaxChar(QUOTE);
      String temporaryId = readXMLNumericChars();
      fieldId = Short.parseShort(temporaryId);
      readXMLSyntaxChar(QUOTE);
      readXMLSyntaxChar(RANGLE);
    }
    ThreeTuple<String,Byte,Short> forReturn = new ThreeTuple<String,Byte,Short>(fieldName.toString(),fieldType.size() > 0 ? getTypeIDForTypeName(fieldType.toByteArray()) : null, fieldId);
    return forReturn;
  }

  private boolean isJSONNumeric(byte b) {
    switch (b) {
    case '+':
    case '-':
    case '.':
    case '0':
    case '1':
    case '2':
    case '3':
    case '4':
    case '5':
    case '6':
    case '7':
    case '8':
    case '9':
    case 'E':
    case 'e':
      return true;
    }
    return false;
  }

  private String readXMLNumericChars() throws TException {
    StringBuilder strbld = new StringBuilder();
    while (true) {
      byte ch = reader_.peek();
      if (!isJSONNumeric(ch)) {
        break;
      }
      strbld.append((char)reader_.read());
    }
    return strbld.toString();
  }

  private long readXMLInteger() throws TException {
    context_.read();
    String str = readXMLNumericChars();
    context_.read();
    try {
      return Long.valueOf(str);
    }
    catch (NumberFormatException ex) {
      throw new TProtocolException(TProtocolException.INVALID_DATA,"Bad data encounted in numeric data");
    }
  }

  private double readJSONDouble() throws TException {
    context_.read();
    Double forReturn;
    try {
      forReturn = Double.valueOf(readXMLNumericChars());
    } catch (NumberFormatException ex) {
      throw new TProtocolException(TProtocolException.INVALID_DATA, "Bad data encounted in numeric data");
    }
    context_.read();
    return forReturn;
  }

  private byte[] readJSONBase64() throws TException {
    context_.read();
    TByteArrayOutputStream arr = readXMLString();
    byte[] b = arr.get();
    int len = arr.len();
    int off = 0;
    int size = 0;
    while (len >= 4) {
      // Decode 4 bytes at a time
      TBase64Utils.decode(b, off, 4, b, size); // NB: decoded in place
      off += 4;
      len -= 4;
      size += 3;
    }
    // Don't decode if we hit the end or got a single leftover byte (invalid
    // base64 but legal for skip of regular string type)
    if (len > 1) {
      // Decode remainder
      TBase64Utils.decode(b, off, len, b, size); // NB: decoded in place
      size += len - 1;
    }
    // Sadly we must copy the byte[] (any way around this?)
    byte [] result = new byte[size];
    System.arraycopy(b, 0, result, 0, size);
    context_.read();
    return result;
  }

  @Override
  public TMessage readMessageBegin() throws TException {
// TODO: FIX
//    readJSONArrayStart();
    if (readXMLInteger() != VERSION) {
      throw new TProtocolException(TProtocolException.BAD_VERSION,
                                   "Message contained bad version.");
    }
    String name;
    try {
      name = readXMLString().toString("UTF-8");
    }
    catch (UnsupportedEncodingException ex) {
      throw new TException("JVM DOES NOT SUPPORT UTF-8");
    }
    byte type = (byte) readXMLInteger();
    int seqid = (int) readXMLInteger();
    return new TMessage(name, type, seqid);
  }

  @Override
  public void readMessageEnd() throws TException {
    // TODO: FIX
    //readJSONArrayEnd();
  }

  @Override
  public TStruct readStructBegin() throws TException {
    pushContext(new XMLPairContext());
    return ANONYMOUS_STRUCT;
  }

  @Override
  public void readStructEnd() throws TException {
    context_.read();
    popContext();
  }

  @Override
  public TField readFieldBegin() throws TException {
    pushContext(new XMLPairContext());
    XMLPairContext tempContext = ((XMLPairContext)context_);
    pushContext(new XMLBaseContext());
    return new TField(tempContext.getName(),tempContext.getName().getBytes()[0] == SLASH[0] ? TType.STOP : tempContext.getType(),tempContext.fieldId == null ? 0 : tempContext.fieldId);
  }

  @Override
  public void readFieldEnd() throws TException {
    popContext();
    context_.read();
    popContext();
  }

  @Override
  public TMap readMapBegin() throws TException {
    readXMLTag();
    byte keyType = getTypeIDForTypeName(readXMLString().toByteArray());
    readXMLTag();
    readXMLTag();
    byte valueType = getTypeIDForTypeName(readXMLString().toByteArray());
    readXMLTag();
    readXMLTag();
    int fieldCount = new Long(readXMLInteger()).intValue(); 
    readXMLTag();
    pushContext(new XMLMapContext(null,null,null));
    return new TMap(keyType,valueType,fieldCount);
  }

  @Override
  public void readMapEnd() throws TException {
    popContext();
  }

  @Override
  public TList readListBegin() throws TException {
    readXMLTag();
    byte elemType = getTypeIDForTypeName(readXMLString().toByteArray());
    readXMLTag();
    readXMLTag();
    int size = (int)readXMLInteger();
    readXMLTag();
    pushContext(new XMLListContext(null,null));
    return new TList(elemType, size);
  }

  @Override
  public void readListEnd() throws TException {
    popContext();
  }

  @Override
  public TSet readSetBegin() throws TException {
	readXMLTag();
	byte elemType = getTypeIDForTypeName(readXMLString().toByteArray());
	readXMLTag();
	readXMLTag();
	int size = (int)readXMLInteger();
	readXMLTag();
    pushContext(new XMLListContext(null,null));
    return new TSet(elemType, size);
  }

  @Override
  public void readSetEnd() throws TException {
    popContext();
  }

  @Override
  public boolean readBool() throws TException {
    return (readXMLInteger() == 0 ? false : true);
  }

  @Override
  public byte readByte() throws TException {
    return (byte) readXMLInteger();
  }

  @Override
  public short readI16() throws TException {
    return (short) readXMLInteger();
  }

  @Override
  public int readI32() throws TException {
    return (int) readXMLInteger();
  }

  @Override
  public long readI64() throws TException {
    return (long) readXMLInteger();
  }

  @Override
  public double readDouble() throws TException {
    return readJSONDouble();
  }

  @Override
  public String readString() throws TException {
    try {
      return readXMLString().toString("UTF-8");
    }
    catch (UnsupportedEncodingException ex) {
      throw new TException("JVM DOES NOT SUPPORT UTF-8");
    }
  }

  @Override
  public ByteBuffer readBinary() throws TException {
    return ByteBuffer.wrap(readJSONBase64());
  }

}
