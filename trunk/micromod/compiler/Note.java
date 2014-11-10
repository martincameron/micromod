
package micromod.compiler;

public class Note implements Element {
	private Macro parent;
	private Repeat child = new Repeat( this );
	private micromod.Note note = new micromod.Note();
	private int fade, repeat, timeStretch;

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
		fade = repeat = timeStretch = 0;
		note.fromString( value );
		if( note.effect == 0xC && note.parameter > 0x40 ) {
			/* Apply x^2 volume-curve for effect C41 to C4F. */
			int vol = ( note.parameter & 0xF ) + 1;
			note.parameter = ( vol * vol ) >> 2;
		}
	}

	public void end() {
		parent.nextNote( note, fade, repeat, timeStretch );
	}

	public void beginFade() {
		fade = 1;
	}

	public void endFade() {
		fade = 2;
	}

	public void beginRepeat() {
		repeat = 1;
	}

	public void endRepeat( int count ) {
		if( count < 2 ) {
			throw new IllegalArgumentException( "Invalid repeat count (2 or more): " + count );
		}
		repeat = count;
	}

	public void setTimeStretch( int rows ) {
		timeStretch = rows;
	}
}
