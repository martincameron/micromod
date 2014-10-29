
package micromod.compiler;

public class Parser {
	public static void parse( java.io.Reader input, Element context ) throws java.io.IOException {
		char[] buf = new char[ 512 ];
		int len, chr = input.read();
		while( true ) {
			while( chr > 0 && chr <= 32 ) {
				/* Skip whitespace.*/
				chr = input.read();
			}
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
			while( chr > 0 && chr <= 32 ) {
				/* Skip whitespace.*/
				chr = input.read();
			}
			while( chr > 32 ) {
				/* Read token.*/
				buf[ len++ ] = ( char ) chr;
				chr = input.read();
			}
			String token = new String( buf, 0, len );
			len = 0;
			while( chr > 0 && chr <= 32 ) {
				/* Skip whitespace.*/
				chr = input.read();
			}
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
			String param = new String( buf, 0, len );
			while( !token.equals( context.getToken() ) ) {
				if( context.getSibling() != null ) {
					context = context.getSibling();
				} else {
					if( context.getParent() != null ) {
						context = context.getParent();
						context.end();
					} else if( token.length() > 0 ){
						throw new IllegalArgumentException( "Invalid token: " + token );
					} else {
						/* Zero-length token means end of file.*/
						return;
					}
				}
			}
			context.begin( param );
			if( context.getChild() != null ) {
				context = context.getChild();
			} else {
				context.end();
			}
		}
	}

	/* Split a string, separated by whitespace or separator. */
	public static String[] split( String input, char separator ) {
		String[] output = new String[ split( input, ',', null ) ];
		split( input, ',', output );
		return output;
	}
	
	public static int split( String input, char separator, String[] output ) {
		int outIdx = 0, inLen = input.length(), start = 0;
		for( int inIdx = 0; inIdx <= inLen; inIdx++ ) {
			char chr = inIdx < inLen ? input.charAt( inIdx ) : separator;
			if( chr < 33 || chr == separator ) {
				if( inIdx > start ) {
					if( output != null && outIdx < output.length ) {
						output[ outIdx ] = input.substring( start, inIdx );
					}
					outIdx++;
				}
				start = inIdx + 1;
			}
		}
		return outIdx;
	}
	
	public static int[] parseIntegerArray( String param ) {
		String[] input = split( param, ',' );
		int[] output = new int[ input.length ];
		for( int idx = 0; idx < input.length; idx++ ) {
			output[ idx ] = parseInteger( input[ idx ] );
		}
		return output;
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
}
