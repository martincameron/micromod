
package micromod.compiler;

public class Decay implements Element {
	private Envelope parent;
	private Element sibling;
	
	public Decay( Envelope parent, Element sibling ) {
		this.parent = parent;
		this.sibling = sibling;
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
