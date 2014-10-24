
package micromod.tracker;

public class Sustain implements Element {
	private Macro parent;
	private Decay sibling;
	
	public Sustain( Macro parent ) {
		this.parent = parent;
		sibling = new Decay( parent );
	}
	
	public String getToken() {
		return "Sustain";
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
		parent.setSustain( Parser.parseInteger( value ) );
	}
	
	public void end() {
	}	
}
