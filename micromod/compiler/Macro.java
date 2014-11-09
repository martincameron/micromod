
package micromod.compiler;

public class Macro implements Envelope {
	private Module parent;
	private Pattern sibling;
	private Scale child = new Scale( this );
	private micromod.Pattern pattern;
	private int macroIdx, rowIdx, repeatCount, attackRows, decayRows;
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
		rowIdx = repeatCount = attackRows = decayRows = 0;
	}
	
	public void end() {
		int rows = rowIdx;
		micromod.Note note = new micromod.Note();
		for( int idx = 1; idx < repeatCount; idx++ ) {
			for( int row = 0; row < rows; row++ ) {
				pattern.getNote( row, 0, note );
				nextNote( note, 0, 0, 0 );
			}
		}
		if( attackRows > 0 || decayRows > 0 ) {
			volumeEnvelope( parent.getModule(), pattern, 0, attackRows, decayRows );
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

	public void nextNote( micromod.Note note, int noteAttackRows, int noteDecayRows, int timeStretchRows ) {
		if( noteAttackRows > 0 || noteDecayRows > 0 ) {
			pattern.setNote( rowIdx, 0, note );
			rowIdx += volumeEnvelope( parent.getModule(), pattern, rowIdx, noteAttackRows, noteDecayRows );
		} else {
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
	}

	public void setAttack( int rows ) {
		attackRows = rows;
	}

	public void setDecay( int rows ) {
		decayRows = rows;
	}

	private static int volumeEnvelope( micromod.Module module, micromod.Pattern pattern, int row, int attackRows, int decayRows ) {
		if( attackRows < 1 ) {
			attackRows = 0;
		}
		if( decayRows < 1 ) {
			decayRows = 0;
		}
		micromod.Note note = new micromod.Note();
		int xVol = 256, yVol = 64;
		pattern.getNote( row, 0, note );
		if( note.effect == 0xC ) {
			yVol = note.parameter;
		} else if( note.instrument > 0 ) {
			yVol = module.getInstrument( note.instrument ).getVolume();
		}
		for( int x = 0; x < 257; x++ ) {
			/* Find value on x^2 volume curve.*/
			if( ( ( x * x ) >> 10 ) == yVol ) {
				xVol = x;
			}
		}
		for( int idx = 0; idx < attackRows; idx++ ) {
			int x = xVol * ( idx + 1 ) * 2 / attackRows;
			x = ( x >> 1 ) + ( x & 1 );
			pattern.getNote( row + idx, 0, note );
			note.effect = 0xC;
			note.parameter = ( ( x * x ) >> 10 );
			pattern.setNote( row + idx, 0, note );
		}
		for( int idx = 0; idx < decayRows; idx++ ) {
			int x = xVol * ( decayRows - idx - 1 ) * 2 / decayRows;
			x = ( x >> 1 ) + ( x & 1 );
			pattern.getNote( row + attackRows + idx, 0, note );
			note.effect = 0xC;
			note.parameter = ( ( x * x ) >> 10 );
			pattern.setNote( row + attackRows + idx, 0, note );
		}
		return attackRows + decayRows;
	}
}
