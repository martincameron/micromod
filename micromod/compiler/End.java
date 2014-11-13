
package micromod.compiler;

public class End implements Element {
	private Note parent;
	private Begin sibling;
	
	public End( Note parent ) {
		this.parent = parent;
		sibling = new Begin( parent );
	}
	
	public String getToken() {
		return "End";
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
		parent.endEffect( value );
	}
	
	public void end() {
	}
}
