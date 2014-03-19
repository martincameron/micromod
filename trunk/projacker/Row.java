package projacker;

public class Row implements Element {
	private int rowIdx;
	private Pattern parent;

	public Row( Pattern parent ) {
		this.parent = parent;
	}
	
	public String getToken() {
		return "Row";
	}
	
	public Element getParent() {
		return parent;
	}
	
	public Element getSibling() {
		return null;
	}
	
	public Element getChild() {
		return null;
	}
	
	public void begin( String row ) {
		int channelIdx = 0;
		char[] input = new char[ 8 ];
		micromod.Note output = new micromod.Note();
		int idx = 0, len = row.length();
		while( idx < len ) {
			int noteIdx = 0;
			while( idx < len && noteIdx < 8 ) {
				input[ noteIdx++ ] = row.charAt( idx++ );
			}
			if( noteIdx == 8 ) {
				parseNote( input, output );
				parent.setNote( rowIdx, channelIdx, output );
			} else {
				throw new IllegalArgumentException( "Pattern " + parent.getPatternIdx() + " Row " + rowIdx + " Channel " + channelIdx + ". Malformed key: " + new String( input, 0, noteIdx ) );
			}
			while( idx < len && row.charAt( idx ) <= 32 ) {
				idx++;
			}
			channelIdx++;							
		}
		rowIdx++;		
	}
	
	public void end() {
	}
	
	public void setRowIdx( int rowIdx ) {
		this.rowIdx = rowIdx;
	}
	
	private void parseNote( char[] input, micromod.Note output ) {
		if( input.length >= 8 ) {
			int key = numChar( input[ 0 ], 11 );
			if( key > 10 ) {
				/* A-G etc.*/
				throw new UnsupportedOperationException( "Fixme." );
			} else {
				/* Decimal note number(1-72).*/
				key = numChar( input[ 0 ], 10 ) * 100 + numChar( input[ 1 ], 10 ) * 10 + numChar( input[ 2 ], 10 );
				if( key < 0 || key > 72 ) {
					throw new IllegalArgumentException( "Pattern " + parent.getPatternIdx() + " Row " + rowIdx + " key out of range (0 to 72): " + key );
				}
			}
			output.key = key;
			int ins = numChar( input[ 3 ], 10 ) * 10 + numChar( input[ 4 ], 10 );
			if( ins < 0 || ins > 31 ) {
				throw new IllegalArgumentException( "Pattern " + parent.getPatternIdx() + " Row " + rowIdx + " instrument out of range (0 to 31): " + ins );
			}
			output.instrument = ins;
			output.effect = numChar( input[ 5 ], 16 );
			output.parameter = ( numChar( input[ 6 ], 16 ) << 4 ) + numChar( input[ 7 ], 16 );
		} else {
			throw new IllegalArgumentException( "Note too short: " + new String( input, 0, input.length ) );
		}
	}
	
	/* Digit of the form [0-9A-Z] or hyphen(0).*/
	private static int numChar( char chr, int radix ) {
		int value = 0;
		if( chr >= '0' && chr <= '9' ) {
			value = chr - '0';
		} else if( chr >= 'A' && chr <= 'Z' ) {
			value = chr + 10 - 'A';
		} else if( chr != '-' ) {
			throw new IllegalArgumentException( "Invalid character: " + chr );
		}
		if( value >= radix ) {
			throw new IllegalArgumentException( "Invalid character: " + chr );
		}
		return value;
	}
}
