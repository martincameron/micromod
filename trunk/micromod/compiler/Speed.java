
package micromod.compiler;

public class Speed implements Element {
	private Macro parent;
	private Note sibling;
	
	public Speed( Macro parent ) {
		this.parent = parent;
		sibling = new Note( parent );
	}
	
	public String getToken() {
		return "Speed";
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
		parent.setSpeed( Parser.parseInteger( value ) );
	}
	
	public void end() {
	}
}
