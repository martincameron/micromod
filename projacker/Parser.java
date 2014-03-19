
package projacker;

public class Parser {
	public static void parse( java.io.Reader input, Element element ) throws java.io.IOException {
		Element context = null;
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
			if( context == null ) {
				context = element;
			} else if( context.getChild() == null ) {
				context.end();
			} else {
				context = context.getChild();
			}
			while( context != null && !token.equals( context.getToken() ) ) {
				if( context.getSibling() != null ) {
					context = context.getSibling();
				} else {
					context = context.getParent();
					if( context != null ) {
						context.end();
					}
				}
			}
			if( context != null ) {
				context.begin( param );
			} else if( token.length() > 0 ) {
				throw new IllegalArgumentException( "Invalid token: " + token );
			}
		}
		while( context != null ) {
			context.end();
			context = context.getParent();
		}
	}
	
	public static int parseInteger( String param ) {
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
	public static int parseIntegerArray( String param, int[] output ) {
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
					throw new IllegalArgumentException( "Invalid character in list: " + param );
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
				throw new IllegalArgumentException( "Invalid character in list: " + param );
			}
		}
		return size;
	}
}
