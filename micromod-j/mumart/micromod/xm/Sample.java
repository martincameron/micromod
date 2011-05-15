
package mumart.micromod.xm;

public class Sample {
	public static final int
		FP_SHIFT = 15,
		FP_ONE = 1 << FP_SHIFT,
		FP_MASK = FP_ONE - 1;

	public String name = "";
	public int volume, panning, rel_note, fine_tune;
	private int loop_start, loop_length;
	private short[] sample_data;
	
	/* Constants for the 16-tap fixed-point sinc interpolator. */
	private static final int LOG2_FILTER_TAPS = 4;
	private static final int FILTER_TAPS = 1 << LOG2_FILTER_TAPS;
	private static final int DELAY = FILTER_TAPS / 2;
	private static final int LOG2_TABLE_ACCURACY = 4;
	private static final int TABLE_ACCURACY = 1 << LOG2_TABLE_ACCURACY;
	private static final int TABLE_INTERP_SHIFT = FP_SHIFT - LOG2_TABLE_ACCURACY;
	private static final int TABLE_INTERP_ONE = 1 << TABLE_INTERP_SHIFT;
	private static final int TABLE_INTERP_MASK = TABLE_INTERP_ONE - 1;
	private static final short[] SINC_TABLE = {
		0,  0,   0,   0,    0,    0,     0, 32767,     0,     0,    0,    0,   0,   0,  0,  0,
	   -1,  7, -31, 103, -279,  671, -1731, 32546,  2006,  -747,  312, -118,  37,  -8,  1,  0,
	   -1, 12, -56, 190, -516, 1246, -3167, 31887,  4259, -1549,  648, -248,  78, -18,  2,  0,
	   -1, 15, -74, 257, -707, 1714, -4299, 30808,  6722, -2375,  994, -384, 122, -29,  4,  0,
	   -2, 17, -87, 305, -849, 2067, -5127, 29336,  9351, -3196, 1338, -520, 169, -41,  6,  0,
	   -2, 18, -93, 334, -941, 2303, -5659, 27510, 12092, -3974, 1662, -652, 214, -53,  8,  0,
	   -1, 17, -95, 346, -985, 2425, -5912, 25375, 14888, -4673, 1951, -771, 257, -65, 10,  0,
	   -1, 16, -92, 341, -985, 2439, -5908, 22985, 17679, -5254, 2188, -871, 294, -76, 13, -1,
	   -1, 15, -85, 323, -945, 2355, -5678, 20399, 20399, -5678, 2355, -945, 323, -85, 15, -1,
	   -1, 13, -76, 294, -871, 2188, -5254, 17679, 22985, -5908, 2439, -985, 341, -92, 16, -1,
		0, 10, -65, 257, -771, 1951, -4673, 14888, 25375, -5912, 2425, -985, 346, -95, 17, -1,
		0,  8, -53, 214, -652, 1662, -3974, 12092, 27510, -5659, 2303, -941, 334, -93, 18, -2,
		0,  6, -41, 169, -520, 1338, -3196,  9351, 29336, -5127, 2067, -849, 305, -87, 17, -2,
		0,  4, -29, 122, -384,  994, -2375,  6722, 30808, -4299, 1714, -707, 257, -74, 15, -1,
		0,  2, -18,  78, -248,  648, -1549,  4259, 31887, -3167, 1246, -516, 190, -56, 12, -1,
		0,  1,  -8,  37, -118,  312,  -747,  2006, 32546, -1731,  671, -279, 103, -31,  7, -1,
		0,  0,   0,   0,    0,    0,     0,     0, 32767,     0,    0,    0,   0,   0,  0,  0
	};

	public void set_sample_data( short[] sample_data, int loop_start, int loop_length, boolean ping_pong ) {
		int sample_length = sample_data.length;
		// Fix loop if necessary.
		if( loop_start < 0 || loop_start > sample_length )
			loop_start = sample_length;
		if( loop_length < 0 || ( loop_start + loop_length ) > sample_length )
			loop_length = sample_length - loop_start;
		sample_length = loop_start + loop_length;
		// Compensate for sinc-interpolator delay.
		loop_start += DELAY;
		// Allocate new sample.
		int new_sample_length = DELAY + sample_length + ( ping_pong ? loop_length : 0 ) + FILTER_TAPS;
		short[] new_sample_data = new short[ new_sample_length ];
		System.arraycopy( sample_data, 0, new_sample_data, DELAY, sample_length );
		sample_data = new_sample_data;
		if( ping_pong ) {
			// Calculate reversed loop.
			int loop_end = loop_start + loop_length;
			for( int idx = 0; idx < loop_length; idx++ )
				sample_data[ loop_end + idx ] = sample_data[ loop_end - idx - 1 ];
			loop_length *= 2;
		}
		// Extend loop for sinc interpolator.
		for( int idx = loop_start + loop_length, end = idx + FILTER_TAPS; idx < end; idx++ )
			sample_data[ idx ] = sample_data[ idx - loop_length ];
		this.sample_data = sample_data;
		this.loop_start = loop_start;
		this.loop_length = loop_length;
	}

	public void resample_nearest( int sample_idx, int sample_frac, int step,
			int left_gain, int right_gain, int[] mix_buffer, int offset, int length ) {
		int loop_len = loop_length;
		int loop_end = loop_start + loop_len;
		sample_idx += DELAY;
		if( sample_idx >= loop_end )
			sample_idx = normalise_sample_idx( sample_idx );
		short[] data = sample_data;
		int out_idx = offset << 1;
		int out_end = ( offset + length ) << 1;
		while( out_idx < out_end ) {
			if( sample_idx >= loop_end ) {
				if( loop_len < 2 ) break;
				while( sample_idx >= loop_end ) sample_idx -= loop_len;
			}
			int y = data[ sample_idx ];
			mix_buffer[ out_idx++ ] += y * left_gain >> FP_SHIFT;
			mix_buffer[ out_idx++ ] += y * right_gain >> FP_SHIFT;
			sample_frac += step;
			sample_idx += sample_frac >> FP_SHIFT;
			sample_frac &= FP_MASK;
		}
	}
	
	public void resample_linear( int sample_idx, int sample_frac, int step,
			int left_gain, int right_gain, int[] mix_buffer, int offset, int length ) {
		int loop_len = loop_length;
		int loop_end = loop_start + loop_len;
		sample_idx += DELAY;
		if( sample_idx >= loop_end )
			sample_idx = normalise_sample_idx( sample_idx );
		short[] data = sample_data;
		int out_idx = offset << 1;
		int out_end = ( offset + length ) << 1;
		while( out_idx < out_end ) {
			if( sample_idx >= loop_end ) {
				if( loop_len < 2 ) break;
				while( sample_idx >= loop_end ) sample_idx -= loop_len;
			}
			int c = data[ sample_idx ];
			int m = data[ sample_idx + 1 ] - c;
			int y = ( m * sample_frac >> FP_SHIFT ) + c;
			mix_buffer[ out_idx++ ] += y * left_gain >> FP_SHIFT;
			mix_buffer[ out_idx++ ] += y * right_gain >> FP_SHIFT;
			sample_frac += step;
			sample_idx += sample_frac >> FP_SHIFT;
			sample_frac &= FP_MASK;
		}
	}

	public void resample_sinc( int sample_idx, int sample_frac, int step,
			int left_gain, int right_gain, int[] mix_buffer, int offset, int length ) {
		int loop_len = loop_length;
		int loop_end = loop_start + loop_len;
		if( sample_idx >= loop_end )
			sample_idx = normalise_sample_idx( sample_idx );
		short[] data = sample_data;
		int out_idx = offset << 1;
		int out_end = ( offset + length ) << 1;
		while( out_idx < out_end ) {
			if( sample_idx >= loop_end ) {
				if( loop_len < 2 ) break;
				while( sample_idx >= loop_end ) sample_idx -= loop_len;
			}
			int table_idx = ( sample_frac >> TABLE_INTERP_SHIFT ) << LOG2_FILTER_TAPS;
			int a1 = 0, a2 = 0;
			a1  = SINC_TABLE[ table_idx + 0  ] * data[ sample_idx + 0  ];
			a1 += SINC_TABLE[ table_idx + 1  ] * data[ sample_idx + 1  ];
			a1 += SINC_TABLE[ table_idx + 2  ] * data[ sample_idx + 2  ];
			a1 += SINC_TABLE[ table_idx + 3  ] * data[ sample_idx + 3  ];
			a1 += SINC_TABLE[ table_idx + 4  ] * data[ sample_idx + 4  ];
			a1 += SINC_TABLE[ table_idx + 5  ] * data[ sample_idx + 5  ];
			a1 += SINC_TABLE[ table_idx + 6  ] * data[ sample_idx + 6  ];
			a1 += SINC_TABLE[ table_idx + 7  ] * data[ sample_idx + 7  ];
			a1 += SINC_TABLE[ table_idx + 8  ] * data[ sample_idx + 8  ];
			a1 += SINC_TABLE[ table_idx + 9  ] * data[ sample_idx + 9  ];
			a1 += SINC_TABLE[ table_idx + 10 ] * data[ sample_idx + 10 ];
			a1 += SINC_TABLE[ table_idx + 11 ] * data[ sample_idx + 11 ];
			a1 += SINC_TABLE[ table_idx + 12 ] * data[ sample_idx + 12 ];
			a1 += SINC_TABLE[ table_idx + 13 ] * data[ sample_idx + 13 ];
			a1 += SINC_TABLE[ table_idx + 14 ] * data[ sample_idx + 14 ];
			a1 += SINC_TABLE[ table_idx + 15 ] * data[ sample_idx + 15 ];
			a2  = SINC_TABLE[ table_idx + 16 ] * data[ sample_idx + 0  ];
			a2 += SINC_TABLE[ table_idx + 17 ] * data[ sample_idx + 1  ];
			a2 += SINC_TABLE[ table_idx + 18 ] * data[ sample_idx + 2  ];
			a2 += SINC_TABLE[ table_idx + 19 ] * data[ sample_idx + 3  ];
			a2 += SINC_TABLE[ table_idx + 20 ] * data[ sample_idx + 4  ];
			a2 += SINC_TABLE[ table_idx + 21 ] * data[ sample_idx + 5  ];
			a2 += SINC_TABLE[ table_idx + 22 ] * data[ sample_idx + 6  ];
			a2 += SINC_TABLE[ table_idx + 23 ] * data[ sample_idx + 7  ];
			a2 += SINC_TABLE[ table_idx + 24 ] * data[ sample_idx + 8  ];
			a2 += SINC_TABLE[ table_idx + 25 ] * data[ sample_idx + 9  ];
			a2 += SINC_TABLE[ table_idx + 26 ] * data[ sample_idx + 10 ];
			a2 += SINC_TABLE[ table_idx + 27 ] * data[ sample_idx + 11 ];
			a2 += SINC_TABLE[ table_idx + 28 ] * data[ sample_idx + 12 ];
			a2 += SINC_TABLE[ table_idx + 29 ] * data[ sample_idx + 13 ];
			a2 += SINC_TABLE[ table_idx + 30 ] * data[ sample_idx + 14 ];
			a2 += SINC_TABLE[ table_idx + 31 ] * data[ sample_idx + 15 ];
			a1 >>= FP_SHIFT;
			a2 >>= FP_SHIFT;
			int y = a1 + ( ( a2 - a1 ) * ( sample_frac & TABLE_INTERP_MASK ) >> TABLE_INTERP_SHIFT );
			mix_buffer[ out_idx++ ] += y * left_gain >> FP_SHIFT;
			mix_buffer[ out_idx++ ] += y * right_gain >> FP_SHIFT;
			sample_frac += step;
			sample_idx += sample_frac >> FP_SHIFT;
			sample_frac &= FP_MASK;
		}
	}

	public int normalise_sample_idx( int sample_idx ) {
		int loop_offset = sample_idx - loop_start;
		if( loop_offset > 0 ) {
			sample_idx = loop_start;
			if( loop_length > 1 ) sample_idx += loop_offset % loop_length;
		}
		return sample_idx;
	}
	
	public void toStringBuffer( StringBuffer out ) {
		out.append( "Name: " + name + '\n' );
		out.append( "Volume: " + volume + '\n' );
		out.append( "Panning: " + panning + '\n' );
		out.append( "Relative Note: " + rel_note + '\n' );
		out.append( "Fine Tune: " + fine_tune + '\n' );
		out.append( "Loop Start: " + loop_start + '\n' );
		out.append( "Loop Length: " + loop_length + '\n' );
		/*
		out.append( "Sample Data: " );
		for( int idx = 0; idx < sample_data.length; idx++ )
			out.append( sample_data[ idx ] + ", " );
		out.append( '\n' );
		*/
	}
}
