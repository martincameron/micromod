
package micromod.compiler;

public class Macro implements Element {
	private Module parent;
	private Pattern sibling;
	private Scale child = new Scale( this );
	private micromod.Pattern pattern;
	private micromod.Note note = new micromod.Note();
	private int macroIdx, rowIdx, repeatCount;
	private int attackRows, sustainRows, decayRows;
	private String scale, root;
	
	public Macro( Module parent ) {
		this.parent = parent;
		sibling = new Pattern( parent );
	}
	
	public String getToken() {
		return "Macro";
	}
	
	public Element getParent() {
		return parent;
	}
	
	public Element getSibling() {
		return sibling;
	}
	
	public Element getChild() {
		return child;
	}
	
	public void begin( String value ) {
		pattern = new micromod.Pattern( 1 );
		macroIdx = Parser.parseInteger( value );
		rowIdx = repeatCount = attackRows = sustainRows = decayRows = 0;
	}
	
	public void end() {
		int rows = rowIdx;
		for( int idx = 1; idx < repeatCount; idx++ ) {
			for( int row = 0; row < rows; row++ ) {
				pattern.getNote( row, 0, note );
				nextNote( note, 0 );
			}
		}
		if( ( attackRows + sustainRows + decayRows ) > 0 ) {
			int xVol = 256, yVol = 64;
			pattern.getNote( 0, 0, note );
			if( note.effect == 0xC ) {
				yVol = note.parameter;
			} else if( note.instrument > 0 ) {
				yVol = parent.getModule().getInstrument( note.instrument ).getVolume();
			}
			for( int x = 0; x < 257; x++ ) {
				/* Find value on x^2 volume curve.*/
				if( ( ( x * x ) >> 10 ) == yVol ) {
					xVol = x;
				}
			}
			for( int row = 0; row < attackRows; row++ ) {
				int x = xVol * ( row + 1 ) * 2 / attackRows;
				x = ( x >> 1 ) + ( x & 1 );
				pattern.getNote( row, 0, note );
				note.effect = 0xC;
				note.parameter = ( ( x * x ) >> 10 );
				pattern.setNote( row, 0, note );
			}
			for( int row = 0; row < sustainRows; row++ ) {
				pattern.getNote( attackRows + row, 0, note );
				note.effect = 0xC;
				note.parameter = yVol;
				pattern.setNote( attackRows + row, 0, note );
			}
			for( int row = 0; row < decayRows; row++ ) {
				int x = xVol * ( decayRows - row - 1 ) * 2 / decayRows;
				x = ( x >> 1 ) + ( x & 1 );
				pattern.getNote( attackRows + sustainRows + row, 0, note );
				note.effect = 0xC;
				note.parameter = ( ( x * x ) >> 10 );
				pattern.setNote( attackRows + sustainRows + row, 0, note );
			}
		}
		parent.setMacro( macroIdx, new micromod.Macro( scale, root, pattern ) );
	}
	
	public void setScale( String scale ) {
		this.scale = scale;
	}
	
	public void setRoot( String root ) {
		this.root = root;
	}

	public void setRepeat( int count ) {
		repeatCount = count;
	}

	public void nextNote( micromod.Note note, int timeStretchRows ) {
		pattern.setNote( rowIdx++, 0, note );
		if( timeStretchRows > 1 ) {
			micromod.Instrument instrument = parent.getModule().getInstrument( note.instrument );
			int sampleLength = instrument.getLoopStart() + instrument.getLoopLength();
			note.effect = 0x9;
			for( int row = 1; row < timeStretchRows; row++ ) {
				int offset = ( sampleLength * row ) / ( timeStretchRows << 7 );
				note.parameter = ( offset >> 1 ) + ( offset & 1 );
				pattern.setNote( rowIdx++, 0, note );
			}
		}
	}

	public void setAttack( int rows ) {
		attackRows = rows;
	}

	public void setSustain( int rows ) {
		sustainRows = rows;
	}

	public void setDecay( int rows ) {
		decayRows = rows;
	}
}
