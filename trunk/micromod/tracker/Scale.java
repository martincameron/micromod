
package micromod.tracker;

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
		System.out.println( getToken() + ": " + value );
		parent.setScale( value );
	}
	
	public void end() {
	}
}
