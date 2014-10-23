
package micromod.tracker;

public class Repeat implements Element {
	private Macro parent;
	private Note sibling;
	
	public Repeat( Macro parent ) {
		this.parent = parent;
		sibling = new Note( parent );
	}
	
	public String getToken() {
		return "Repeat";
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
		parent.setRepeat( Parser.parseInteger( value ) );
	}
	
	public void end() {
	}
}
