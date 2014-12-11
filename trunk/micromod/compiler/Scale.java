
package micromod.compiler;

public class Scale implements Element {
	private Macro parent;
	private Root sibling;
	
	public Scale( Macro parent ) {
		this.parent = parent;
		sibling = new Root( parent );
	}
	
	public String getToken() {
		return "Scale";
	}
	
	public Element getParent() {
		return parent;
	}
	
	public Element getSibling() {
		return sibling;
	}
	
	public Element getChild() {
		return null;
	}
	
	public void begin( String value ) {
		parent.setScale( value );
	}
	
	public void end() {
	}

	public String description() {
		return "\"C#D#EF#G#A#B\" (Key signature of the Macro, C-Major would be 'C-D-EF-G-A-B', for example.)";
	}
}
