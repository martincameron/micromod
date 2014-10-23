
package micromod.tracker;

public class TimeStretch implements Element {
	private Note parent;
	private FadeIn sibling;
	
	public TimeStretch( Note parent ) {
		this.parent = parent;
		sibling = new FadeIn( parent );
	}
	
	public String getToken() {
		return "TimeStretch";
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
		parent.setTimeStretch( Parser.parseInteger( value ) );
	}
	
	public void end() {
	}	
}
