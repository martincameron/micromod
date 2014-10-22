
package micromod.tracker;

public class Note implements Element {
	private Macro parent;
	private TimeStretch child = new TimeStretch( this );
	private micromod.Note note = new micromod.Note();
	private int timeStretchRows;
	
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
		timeStretchRows = 0;
	}
	
	public void end() {
		parent.nextNote( note );
		if( timeStretchRows > 1 ) {
			micromod.Instrument instrument = parent.getModule().getInstrument( note.instrument );
			int sampleLength = instrument.getLoopStart() + instrument.getLoopLength();
			note.effect = 0x9;
			for( int row = 1; row < timeStretchRows; row++ ) {
				int offset = ( sampleLength * row ) / ( timeStretchRows << 7 );
				note.parameter = ( offset >> 1 ) + ( offset & 1 );
				parent.nextNote( note );
			}
		}
	}

	public void setTimeStretch( int rows ) {
		timeStretchRows = rows;
	}
}
