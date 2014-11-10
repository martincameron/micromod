
package micromod.compiler;

public class Decay implements Element {
	private Macro parent;
	private Note sibling;
	
	public Decay( Macro parent ) {
		this.parent = parent;
		this.sibling = new Note( parent );
	}
	
	public String getToken() {
		return "Decay";
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
		parent.setDecay( Parser.parseInteger( value ) );
	}
	
	public void end() {
	}	
}
