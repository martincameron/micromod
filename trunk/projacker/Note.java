
package projacker;

public class Note implements Element {
	private Macro parent;
	private micromod.Note note = new micromod.Note();
	
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
		return null;
	}
	
	public void begin( String value ) {
		note.fromString( value );
		parent.nextNote( note );
	}
	
	public void end() {
	}	
}
