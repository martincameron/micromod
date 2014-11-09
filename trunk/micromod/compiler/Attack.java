
package micromod.compiler;

public class Attack implements Element {
	private Envelope parent;
	private Element sibling;
	
	public Attack( Envelope parent, Element sibling ) {
		this.parent = parent;
		this.sibling = sibling;
	}
	
	public String getToken() {
		return "Attack";
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
		parent.setAttack( Parser.parseInteger( value ) );
	}
	
	public void end() {
	}	
}
