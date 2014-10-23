
package micromod.tracker;

public class Note implements Element {
	private Macro parent;
	private TimeStretch child = new TimeStretch( this );
	private micromod.Note note = new micromod.Note();
	private int timeStretchRows, fadeInRows, fadeOutRows;
	
	public Note( Macro parent ) {
		this.parent = parent;
	}
	
	public String getToken() {
		return "Note";
	}
	
	public Element getParent() {
		return parent;
	}
	
	public Element getSibling() {
		return null;
	}
	
	public Element getChild() {
		return child;
	}
	
	public void begin( String value ) {
		note.fromString( value );
		timeStretchRows = fadeInRows = fadeOutRows = 0;
	}
	
	public void end() {
		if( timeStretchRows > 1 ) {
			parent.nextNote( note );
			micromod.Instrument instrument = parent.getModule().getInstrument( note.instrument );
			int sampleLength = instrument.getLoopStart() + instrument.getLoopLength();
			note.effect = 0x9;
			for( int row = 1; row < timeStretchRows; row++ ) {
				int offset = ( sampleLength * row ) / ( timeStretchRows << 7 );
				note.parameter = ( offset >> 1 ) + ( offset & 1 );
				parent.nextNote( note );
			}
		} else if( fadeInRows > 0 ) {
			int x0 = 0, y0 = note.parameter;
			for( int x = 0; x < 257; x++ ) {
				if( ( ( x * x ) >> 10 ) == y0 ) x0 = x;
			}
			note.effect = 0xC;
			note.parameter = 0;
			parent.nextNote( note );
			note.key = note.instrument = 0;
			for( int row = 1; row < fadeInRows; row++ ) {
				int x = x0 * ( row + 1 ) / fadeInRows;
				note.parameter = ( x * x ) >> 10;
				parent.nextNote( note );
			}
		} else if( fadeOutRows > 0 ) {
			int x0 = 0, y0 = note.parameter;
			for( int x = 0; x < 257; x++ ) {
				if( ( ( x * x ) >> 10 ) == y0 ) x0 = x;
			}
			note.effect = 0xC;
			parent.nextNote( note );
			note.key = note.instrument = 0;
			for( int row = 1; row < fadeOutRows; row++ ) {
				int x = x0 * ( fadeOutRows - row ) / fadeOutRows;
				note.parameter = ( x * x ) >> 10;
				parent.nextNote( note );
			}
		} else {
			parent.nextNote( note );
		}
	}

	public void setTimeStretch( int rows ) {
		timeStretchRows = rows;
	}

	public void setFadeIn( int rows ) {
		fadeInRows = rows;
	}

	public void setFadeOut( int rows ) {
		fadeOutRows = rows;
	}
}
