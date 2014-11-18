
package micromod.compiler;

public class Note implements Element {
	private Macro parent;
	private TimeStretch child = new TimeStretch( this );
	private micromod.Note note = new micromod.Note();
	private int repeat, timeStretch;

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
		repeat = timeStretch = 0;
		note.fromString( value );
	}

	public void end() {
		parent.nextNote( note, repeat, timeStretch );
	}

	public void setTimeStretch( int rows ) {
		timeStretch = rows;
	}

	public void setRepeat( int count ) {
		repeat = count;
	}
}
