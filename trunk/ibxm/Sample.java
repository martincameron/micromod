
package ibxm;

public class Sample {
	public static final int
		FP_SHIFT = 15,
		FP_ONE = 1 << FP_SHIFT,
		FP_MASK = FP_ONE - 1;

	public static final int C2_PAL = 8287, C2_NTSC = 8363;

	public String name = "";
	public int volume = 0, panning = -1, relNote = 0, fineTune = 0, c2Rate = C2_NTSC;
	private int loopStart = 0, loopLength = 0;
	private short[] sampleData;
	
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

	public void setSampleData( short[] sampleData, int loopStart, int loopLength, boolean pingPong ) {
		int sampleLength = sampleData.length;
		// Fix loop if necessary.
		if( loopStart < 0 || loopStart > sampleLength )
			loopStart = sampleLength;
		if( loopLength < 0 || ( loopStart + loopLength ) > sampleLength )
			loopLength = sampleLength - loopStart;
		sampleLength = loopStart + loopLength;
		// Compensate for sinc-interpolator delay.
		loopStart += DELAY;
		// Allocate new sample.
		int newSampleLength = DELAY + sampleLength + ( pingPong ? loopLength : 0 ) + FILTER_TAPS;
		short[] newSampleData = new short[ newSampleLength ];
		System.arraycopy( sampleData, 0, newSampleData, DELAY, sampleLength );
		sampleData = newSampleData;
		if( pingPong ) {
			// Calculate reversed loop.
			int loopEnd = loopStart + loopLength;
			for( int idx = 0; idx < loopLength; idx++ )
				sampleData[ loopEnd + idx ] = sampleData[ loopEnd - idx - 1 ];
			loopLength *= 2;
		}
		// Extend loop for sinc interpolator.
		for( int idx = loopStart + loopLength, end = idx + FILTER_TAPS; idx < end; idx++ )
			sampleData[ idx ] = sampleData[ idx - loopLength ];
		this.sampleData = sampleData;
		this.loopStart = loopStart;
		this.loopLength = loopLength;
	}

	public void resampleNearest( int sampleIdx, int sampleFrac, int step,
			int leftGain, int rightGain, int[] mixBuffer, int offset, int length ) {
		int loopLen = loopLength;
		int loopEnd = loopStart + loopLen;
		sampleIdx += DELAY;
		if( sampleIdx >= loopEnd )
			sampleIdx = normaliseSampleIdx( sampleIdx );
		short[] data = sampleData;
		int outIdx = offset << 1;
		int outEnd = ( offset + length ) << 1;
		while( outIdx < outEnd ) {
			if( sampleIdx >= loopEnd ) {
				if( loopLen < 2 ) break;
				while( sampleIdx >= loopEnd ) sampleIdx -= loopLen;
			}
			int y = data[ sampleIdx ];
			mixBuffer[ outIdx++ ] += y * leftGain >> FP_SHIFT;
			mixBuffer[ outIdx++ ] += y * rightGain >> FP_SHIFT;
			sampleFrac += step;
			sampleIdx += sampleFrac >> FP_SHIFT;
			sampleFrac &= FP_MASK;
		}
	}
	
	public void resampleLinear( int sampleIdx, int sampleFrac, int step,
			int leftGain, int rightGain, int[] mixBuffer, int offset, int length ) {
		int loopLen = loopLength;
		int loopEnd = loopStart + loopLen;
		sampleIdx += DELAY;
		if( sampleIdx >= loopEnd )
			sampleIdx = normaliseSampleIdx( sampleIdx );
		short[] data = sampleData;
		int outIdx = offset << 1;
		int outEnd = ( offset + length ) << 1;
		while( outIdx < outEnd ) {
			if( sampleIdx >= loopEnd ) {
				if( loopLen < 2 ) break;
				while( sampleIdx >= loopEnd ) sampleIdx -= loopLen;
			}
			int c = data[ sampleIdx ];
			int m = data[ sampleIdx + 1 ] - c;
			int y = ( m * sampleFrac >> FP_SHIFT ) + c;
			mixBuffer[ outIdx++ ] += y * leftGain >> FP_SHIFT;
			mixBuffer[ outIdx++ ] += y * rightGain >> FP_SHIFT;
			sampleFrac += step;
			sampleIdx += sampleFrac >> FP_SHIFT;
			sampleFrac &= FP_MASK;
		}
	}

	public void resampleSinc( int sampleIdx, int sampleFrac, int step,
			int leftGain, int rightGain, int[] mixBuffer, int offset, int length ) {
		int loopLen = loopLength;
		int loopEnd = loopStart + loopLen;
		if( sampleIdx >= loopEnd )
			sampleIdx = normaliseSampleIdx( sampleIdx );
		short[] data = sampleData;
		int outIdx = offset << 1;
		int outEnd = ( offset + length ) << 1;
		while( outIdx < outEnd ) {
			if( sampleIdx >= loopEnd ) {
				if( loopLen < 2 ) break;
				while( sampleIdx >= loopEnd ) sampleIdx -= loopLen;
			}
			int tableIdx = ( sampleFrac >> TABLE_INTERP_SHIFT ) << LOG2_FILTER_TAPS;
			int a1 = 0, a2 = 0;
			a1  = SINC_TABLE[ tableIdx + 0  ] * data[ sampleIdx + 0  ];
			a1 += SINC_TABLE[ tableIdx + 1  ] * data[ sampleIdx + 1  ];
			a1 += SINC_TABLE[ tableIdx + 2  ] * data[ sampleIdx + 2  ];
			a1 += SINC_TABLE[ tableIdx + 3  ] * data[ sampleIdx + 3  ];
			a1 += SINC_TABLE[ tableIdx + 4  ] * data[ sampleIdx + 4  ];
			a1 += SINC_TABLE[ tableIdx + 5  ] * data[ sampleIdx + 5  ];
			a1 += SINC_TABLE[ tableIdx + 6  ] * data[ sampleIdx + 6  ];
			a1 += SINC_TABLE[ tableIdx + 7  ] * data[ sampleIdx + 7  ];
			a1 += SINC_TABLE[ tableIdx + 8  ] * data[ sampleIdx + 8  ];
			a1 += SINC_TABLE[ tableIdx + 9  ] * data[ sampleIdx + 9  ];
			a1 += SINC_TABLE[ tableIdx + 10 ] * data[ sampleIdx + 10 ];
			a1 += SINC_TABLE[ tableIdx + 11 ] * data[ sampleIdx + 11 ];
			a1 += SINC_TABLE[ tableIdx + 12 ] * data[ sampleIdx + 12 ];
			a1 += SINC_TABLE[ tableIdx + 13 ] * data[ sampleIdx + 13 ];
			a1 += SINC_TABLE[ tableIdx + 14 ] * data[ sampleIdx + 14 ];
			a1 += SINC_TABLE[ tableIdx + 15 ] * data[ sampleIdx + 15 ];
			a2  = SINC_TABLE[ tableIdx + 16 ] * data[ sampleIdx + 0  ];
			a2 += SINC_TABLE[ tableIdx + 17 ] * data[ sampleIdx + 1  ];
			a2 += SINC_TABLE[ tableIdx + 18 ] * data[ sampleIdx + 2  ];
			a2 += SINC_TABLE[ tableIdx + 19 ] * data[ sampleIdx + 3  ];
			a2 += SINC_TABLE[ tableIdx + 20 ] * data[ sampleIdx + 4  ];
			a2 += SINC_TABLE[ tableIdx + 21 ] * data[ sampleIdx + 5  ];
			a2 += SINC_TABLE[ tableIdx + 22 ] * data[ sampleIdx + 6  ];
			a2 += SINC_TABLE[ tableIdx + 23 ] * data[ sampleIdx + 7  ];
			a2 += SINC_TABLE[ tableIdx + 24 ] * data[ sampleIdx + 8  ];
			a2 += SINC_TABLE[ tableIdx + 25 ] * data[ sampleIdx + 9  ];
			a2 += SINC_TABLE[ tableIdx + 26 ] * data[ sampleIdx + 10 ];
			a2 += SINC_TABLE[ tableIdx + 27 ] * data[ sampleIdx + 11 ];
			a2 += SINC_TABLE[ tableIdx + 28 ] * data[ sampleIdx + 12 ];
			a2 += SINC_TABLE[ tableIdx + 29 ] * data[ sampleIdx + 13 ];
			a2 += SINC_TABLE[ tableIdx + 30 ] * data[ sampleIdx + 14 ];
			a2 += SINC_TABLE[ tableIdx + 31 ] * data[ sampleIdx + 15 ];
			a1 >>= FP_SHIFT;
			a2 >>= FP_SHIFT;
			int y = a1 + ( ( a2 - a1 ) * ( sampleFrac & TABLE_INTERP_MASK ) >> TABLE_INTERP_SHIFT );
			mixBuffer[ outIdx++ ] += y * leftGain >> FP_SHIFT;
			mixBuffer[ outIdx++ ] += y * rightGain >> FP_SHIFT;
			sampleFrac += step;
			sampleIdx += sampleFrac >> FP_SHIFT;
			sampleFrac &= FP_MASK;
		}
	}

	public int normaliseSampleIdx( int sampleIdx ) {
		int loopOffset = sampleIdx - loopStart;
		if( loopOffset > 0 ) {
			sampleIdx = loopStart;
			if( loopLength > 1 ) sampleIdx += loopOffset % loopLength;
		}
		return sampleIdx;
	}
	
	public boolean looped() {
		return loopLength > 1;
	}

	public void toStringBuffer( StringBuffer out ) {
		out.append( "Name: " + name + '\n' );
		out.append( "Volume: " + volume + '\n' );
		out.append( "Panning: " + panning + '\n' );
		out.append( "Relative Note: " + relNote + '\n' );
		out.append( "Fine Tune: " + fineTune + '\n' );
		out.append( "Loop Start: " + loopStart + '\n' );
		out.append( "Loop Length: " + loopLength + '\n' );
		/*
		out.append( "Sample Data: " );
		for( int idx = 0; idx < sampleData.length; idx++ )
			out.append( sampleData[ idx ] + ", " );
		out.append( '\n' );
		*/
	}
}
