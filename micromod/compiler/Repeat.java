
package micromod.compiler;

public class Repeat implements Element {
	private Note parent;
	private End sibling;
	
	public Repeat( Note parent ) {
		this.parent = parent;
		sibling = new End( parent );
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
		parent.endRepeat( Parser.parseInteger( value ) );
	}

	public void end() {
	}
}
