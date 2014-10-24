
package micromod.tracker;

public class Attack implements Element {
	private Macro parent;
	private Sustain sibling;
	
	public Attack( Macro parent ) {
		this.parent = parent;
		sibling = new Sustain( parent );
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
