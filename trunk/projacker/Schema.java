
package projacker;

public class Schema {
	private String name;
	private Schema parent, child, sibling;

	public Schema( java.io.Reader reader ) throws java.io.IOException {
		char[] buf = new char[ 128 ];
		int len = 0, chr = reader.read();
		while( chr >= '0' ) {
			buf[ len++ ] = ( char ) chr;
			chr = reader.read();
		}
		name = new String( buf, 0, len );
		if( chr == '(' ) {
			child = new Schema( reader );
			Schema schema = child;
			while( schema != null ) {
				schema.parent = this;
				schema = schema.sibling;
			}
			chr = reader.read();
		}
		if( chr == ',' ) {
			sibling = new Schema( reader );
		}
	}

	public String getName() {
		return name;
	}

	public Schema getParent() { 
		return parent;
	}

	public void parse( java.io.Reader input, Handler handler ) throws java.io.IOException {
		Schema schema = this;
		Value value = null;
		char[] buf = new char[ 512 ];
		int len, chr = input.read();
		while( chr > 0 && chr <= 32 ) {
			/* Skip whitespace.*/
			chr = input.read();
		}
		while( chr > 0 ) {
			while( chr == '(' ) {
				/* Skip comments.*/
				while( chr > 0 && chr != ')' ) {
					chr = input.read();
				}
				if( chr == ')' ) {
					chr = input.read();
				}
				while( chr > 0 && chr <= 32 ) {
					chr = input.read();
				}
			}
			len = 0;
			while( chr > 32 ) {
				/* Read token.*/
				buf[ len++ ] = ( char ) chr;
				chr = input.read();
			}
			while( chr > 0 && chr <= 32 ) {
				/* Skip whitespace.*/
				chr = input.read();
			}
			String token = new String( buf, 0, len );
			len = 0;
			if( chr == '"' ) {
				/* Quote-delimited value. */
				chr = input.read();
				while( chr > 0 && chr != '"' ) {
					buf[ len++ ] = ( char ) chr;
					chr = input.read();
				}
				if( chr == '"' ) {
					chr = input.read();
				}
			} else {
				/* Whitespace-delimited value.*/
				while( chr > 32 ) {
					buf[ len++ ] = ( char ) chr;
					chr = input.read();
				}
			}
			while( chr > 0 && chr <= 32 ) {
				/* Skip whitespace.*/
				chr = input.read();
			}
			String param = new String( buf, 0, len );
			schema = schema.next( token );
			value = new Value( schema, value, param );
			handler.value( value );
		}
	}

	private Schema next( String token ) {
		Schema schema = child != null ? child : this;
		while( schema != null ) {
			Schema parent = schema.parent;
			while( schema != null ) {
				if( schema.name.equals( token ) ) {
					return schema;
				}
				schema = schema.sibling;
			}
			schema = parent;
		}
		throw new IllegalArgumentException( "Invalid token: " + token );
	}
	
	public interface Handler {
		/* The associated token is given by value.getSchema().getName(). */
		public void value( Value value );
	}
	
	public static class Value {
		private Schema schema;
		private Value parent;
		private String param;
		
		public Value( Schema schema, Value parent, String param ) {
			this.schema = schema;
			this.parent = parent;
			this.param = param;
		}
		
		public Value getParent() {
			return parent;
		}
		
		public Schema getSchema() {
			return schema;
		}
		
		public String toString() {
			return param;
		}

		public int toInteger() {
			int idx = 0, len = param.length(), a = 0, s = 1;
			if( idx < len ) {
				char chr = param.charAt( idx++ );
				if( idx < len && chr == '-' ) {
					s = -1;
					chr = param.charAt( idx++ );
				}
				while( chr >= '0' && chr <= '9' ) {
					a = a * 10 + chr - '0';
					chr = idx < len ? param.charAt( idx++ ) : ',';
				}
				if( idx < len ) {
					throw new IllegalArgumentException( "Invalid character: " + chr );
				}
			}
			return a * s;
		}

		/* The size of the array is returned, output may be null.*/
		public int toIntegerArray( int[] output ) {
			int size = 0;
			int idx = 0, len = param.length();
			while( idx < len ) {
				int a = 0, s = 1;
				char chr = param.charAt( idx++ );
				if( idx < len && chr == '-' ) {
					s = -1;
					chr = param.charAt( idx++ );
				}
				while( chr >= '0' && chr <= '9' ) {
					a = a * 10 + chr - '0';
					chr = idx < len ? param.charAt( idx++ ) : ',';
				}
				a = a * s;
				if( chr == '>' ) {
					int b = 0, t = 1;
					if( idx < len ) {
						chr = param.charAt( idx++ );
					}
					if( idx < len && chr == '-' ) {
						t = -1;
						chr = param.charAt( idx++ );
					}
					while( chr >= '0' && chr <= '9' ) {
						b = b * 10 + chr - '0';
						chr = idx < len ? param.charAt( idx++ ) : ',';
					}
					b = b * t;
					if( chr != ',' ) {
						throw new IllegalArgumentException( "Invalid value: " + schema.getName() + " " + param );
					}
					int d = a > b ? -1 : 1;
					int e = a > b ? b - 1 : b + 1;
					while( a != e ) {
						if( output != null && size < output.length ) {
							output[ size ] = a;
						}
						a += d;
						size += 1;
					}
				} else if( chr == ',' ) {
					if( output != null && size < output.length ) {
						output[ size ] = a;
					}
					size += 1;
				} else {
					throw new IllegalArgumentException( "Invalid value: " + schema.getName() + " " + param );
				}
			}
			return size;
		}
	}
}
