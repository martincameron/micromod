
package micromod.tracker;

public class FadeIn implements Element {
	private Note parent;
	private FadeOut sibling;
	
	public FadeIn( Note parent ) {
		this.parent = parent;
		sibling = new FadeOut( parent );
	}
	
	public String getToken() {
		return "FadeIn";
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
		parent.setFadeIn( Parser.parseInteger( value ) );
	}
	
	public void end() {
	}	
}
