
package micromod.compiler;

public class Note implements Element {
	private Macro parent;
	private Repeat child = new Repeat( this );
	private micromod.Note note = new micromod.Note();
	private int repeatCount;

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
		repeatCount = 0;
		note.fromString( value );
	}

	public void end() {
		parent.nextNote( note, repeatCount );
	}

	public void setRepeat( int count ) {
		if( repeatCount < 1 ) {
			repeatCount = count;
		} else {
			repeatCount *= count;
		}
	}
}
