
package micromod.compiler;

public class Macro implements Element {
	public static final int BEGIN = 1, END = 2;

	private Module parent;
	private Pattern sibling;
	private Scale child = new Scale( this );
	private micromod.Pattern pattern;
	private int macroIdx, rowIdx;
	private int fadeRow, repeatRow, modulationRow;
	private int attackRows, decayRows, speed;
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
		rowIdx = fadeRow = repeatRow = modulationRow = 0;
		attackRows = decayRows = 0;
		speed = 6;
	}

	public void end() {
		int volume = getNoteVolume( parent.getModule(), pattern, 0 );
		if( attackRows > 0 ) {
			volumeFade( pattern, 0, attackRows - 1, 0, volume );
		}
		if( attackRows >= 0 && decayRows > 0 ) {
			volumeFade( pattern, attackRows, attackRows + decayRows - 1, volume, 0 );
		}
		parent.setMacro( macroIdx, new micromod.Macro( scale, root, pattern ) );
	}

	public void setScale( String scale ) {
		this.scale = scale;
	}
	
	public void setRoot( String root ) {
		this.root = root;
	}

	public void nextNote( micromod.Note note, int fadeParam, int repeatParam, int portaSemitones, int timeStretchRows ) {
		micromod.Module module = parent.getModule();
		if( portaSemitones != 0 && note.key > 0 ) {
			int fineTune = ( note.instrument > 0 ) ? module.getInstrument( note.instrument ).getFineTune() : 0;
			int period = note.keyToPeriod( note.key + portaSemitones, fineTune ) - note.keyToPeriod( note.key, fineTune );
			if( period < 0 ) { /* Porta Up. */
				note.effect = 0x1;
				period = -period;
			} else { /* Porta Down. */
				note.effect = 0x2;
			}
			int delta = ( speed > 1 ) ? period * 2 / ( speed - 1 ) : 0;
			note.parameter = ( delta >> 1 ) + ( delta & 1 );
		}
		pattern.setNote( rowIdx++, 0, note );
		if( timeStretchRows > 1 ) {
			micromod.Instrument instrument = module.getInstrument( note.instrument );
			int sampleLength = instrument.getLoopStart() + instrument.getLoopLength();
			note.effect = 0x9;
			for( int row = 1; row < timeStretchRows; row++ ) {
				int offset = ( sampleLength * row ) / ( timeStretchRows << 7 );
				note.parameter = ( offset >> 1 ) + ( offset & 1 );
				pattern.setNote( rowIdx++, 0, note );
			}
		}
		if( repeatParam == BEGIN ) {
			repeatRow = rowIdx;
		} else if( repeatParam > 1 ) {
			int repeatEnd = rowIdx;
			for( int idx = 1; idx < repeatParam; idx++ ) {
				for( int row = repeatRow; row < repeatEnd; row++ ) {
					pattern.getNote( row, 0, note );
					pattern.setNote( rowIdx++, 0, note );
				}
			}
		}
		if( fadeParam == BEGIN ) {
			fadeRow = rowIdx;
		} else if( fadeParam == END ) {
			int startVol = getNoteVolume( module, pattern, fadeRow );
			int endVol = getNoteVolume( module, pattern, rowIdx -1 );
			volumeFade( pattern, fadeRow, rowIdx - 1, startVol, endVol );
		}
	}

	public void setSpeed( int ticksPerRow ) {
		speed = ticksPerRow;
	}

	public void setAttack( int rows ) {
		attackRows = rows;
	}

	public void setDecay( int rows ) {
		decayRows = rows;
	}

	private static void volumeFade( micromod.Pattern pattern, int startRow, int endRow, int startVol, int endVol ) {
		int v0 = sqrt( startVol );
		int v1 = sqrt( endVol );
		int rows = endRow - startRow + 1;
		int vm = ( v1 - v0 ) * 2 / rows;
		for( int row = 0; row < rows; row++ ) {
			int volume = vm * ( row + 1 ) + v0 * 2;
			volume = ( volume >> 1 ) + ( volume & 1 );
			setNoteVolume( pattern, startRow + row, ( volume * volume ) >> 10 );
		}
	}

	private static int getNoteVolume( micromod.Module module, micromod.Pattern pattern, int rowIdx ) {
		micromod.Note note = new micromod.Note();
		pattern.getNote( rowIdx, 0, note );
		int volume = 0;
		if( note.effect == 0xC ) {
			volume = note.parameter;
		} else if( note.instrument > 0 ) {
			volume = module.getInstrument( note.instrument ).getVolume();
		}
		return volume;
	}

	private static void setNoteVolume( micromod.Pattern pattern, int rowIdx, int volume ) {
		micromod.Note note = new micromod.Note();
		pattern.getNote( rowIdx, 0, note );
		note.effect = 0xC;
		note.parameter = volume;
		pattern.setNote( rowIdx, 0, note );
	}

	private static int sqrt( int y ) {
		/* Returns 32 * sqrt(x) for x in the range 0 to 64. */
		for( int x = 0; x < 257; x++ ) {
			if( ( ( x * x ) >> 10 ) == y ) {
				return x;
			}
		}
		return 256;
	}
}
