
package ibxm;

public class Envelope {
	public boolean enabled, sustain, looped;
	public int sustain_tick, loop_start_tick, loop_end_tick;
	public int num_points = 1;
	public int[] points_tick = new int[ 1 ];
	public int[] points_ampl = new int[ 1 ];
	
	public int next_tick( int tick, boolean key_on ) {
		tick++;
		if( looped && tick >= loop_end_tick ) tick = loop_start_tick;
		if( sustain && key_on && tick >= sustain_tick ) tick = sustain_tick;
		return tick;
	}
	
	public int calculate_ampl( int tick ) {
		int ampl = points_ampl[ num_points - 1 ];
		if( tick < points_tick[ num_points - 1 ] ) {
			int point = 0;
			for( int idx = 1; idx < num_points; idx++ )
				if( points_tick[ idx ] <= tick ) point = idx;
			int dt = points_tick[ point + 1 ] - points_tick[ point ];
			int da = points_ampl[ point + 1 ] - points_ampl[ point ];
			ampl = points_ampl[ point ];
			ampl += ( ( da << 24 ) / dt ) * ( tick - points_tick[ point ] ) >> 24;
		}
		return ampl;
	}
	
	public void toStringBuffer( StringBuffer out ) {
		out.append( "Enabled: " + enabled + '\n' );
		out.append( "Sustain: " + sustain + '\n' );
		out.append( "Looped: " + looped + '\n' );
		out.append( "Sustain Tick: " + sustain_tick + '\n' );
		out.append( "Loop Start Tick: " + loop_start_tick + '\n' );
		out.append( "Loop End Tick: " + loop_end_tick + '\n' );
		out.append( "Num Points: " + num_points + '\n' );
		out.append( "Points: " );
		for( int point = 0; point < num_points; point++ ) {
			out.append( "(" + points_tick[ point ] + ", " + points_ampl[ point ] + "), " );
		}
		out.append( '\n' );
	}
}
