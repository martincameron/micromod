
package micromod;

public class Instrument {
	public static final int FP_SHIFT = 15, FP_ONE = 1 << FP_SHIFT, FP_MASK = FP_ONE - 1;
	
	private String name = "";
	private int volume, fineTune, loopStart, loopLength;
	private byte[] sampleData = new byte[ 0 ];
	
	public String getName() {
		return name;
	}
	
	public void setName( String name ) {
		this.name = ( name != null ) ? name : "";
	}
	
	public int getVolume() {
		return volume;
	}
	
	public void setVolume( int volume ) {
		if( volume < 0 || volume > 64 ) {
			throw new IllegalArgumentException( "Instrument volume out of range (0 to 64): " + volume );
		}
		this.volume = volume;
	}
	
	public int getFineTune() {
		return fineTune;
	}
	
	public void setFineTune( int fineTune ) {
		if( fineTune < -8 || fineTune > 7 ) {
			throw new IllegalArgumentException( "Instrument fine tune out of range (-8 to 7): " + fineTune );
		}
		this.fineTune = fineTune;
	}
	
	public void setSampleData( byte[] sampleData, int loopStart, int loopLength ) {
		int sampleLength = sampleData.length;
		if( loopStart + loopLength > sampleLength ) {
			loopLength = sampleLength - loopStart;
		}
		if( loopStart < 0 || loopStart >= sampleLength || loopLength < 2 ) {
			loopStart = sampleLength;
			loopLength = 0;
		}
		this.loopStart = loopStart;
		this.loopLength = loopLength;
		this.sampleData = new byte[ loopStart + loopLength + 1 ];
		System.arraycopy( sampleData, 0, this.sampleData, 0, loopStart + loopLength );
		/* The sample after the loop end must be the same as the loop start for the interpolation algorithm. */
		this.sampleData[ loopStart + loopLength ] = this.sampleData[ loopStart ];
	}
	
	public int getLoopStart() {
		return loopStart;
	}
	
	public int getLoopLength() {
		return loopLength;
	}
	
	public void getAudio( int sampleIdx, int sampleFrac, int step, int leftGain, int rightGain,
		int[] mixBuffer, int offset, int count, boolean interpolation ) {
		int loopEnd = loopStart + loopLength;
		int mixIdx = offset << 1;
		int mixEnd = ( offset + count ) << 1;
		if( interpolation ) {
			while( mixIdx < mixEnd ) {
				if( sampleIdx >= loopEnd ) {
					if( loopLength < 2 ) {
						break;
					}
					while( sampleIdx >= loopEnd ) {
						sampleIdx -= loopLength;
					}
				}
				int c = sampleData[ sampleIdx ];
				int m = sampleData[ sampleIdx + 1 ] - c;
				int y = ( ( m * sampleFrac ) >> ( FP_SHIFT - 8 ) ) + ( c << 8 );
				mixBuffer[ mixIdx++ ] += ( y * leftGain ) >> FP_SHIFT;
				mixBuffer[ mixIdx++ ] += ( y * rightGain ) >> FP_SHIFT;
				sampleFrac += step;
				sampleIdx += sampleFrac >> FP_SHIFT;
				sampleFrac &= FP_MASK;
			}
		} else {
			while( mixIdx < mixEnd ) {
				if( sampleIdx >= loopEnd ) {
					if( loopLength < 2 ) {
						break;
					}
					while( sampleIdx >= loopEnd ) {
						sampleIdx -= loopLength;
					}
				}
				int y = sampleData[ sampleIdx ] << 8;
				mixBuffer[ mixIdx++ ] += ( y * leftGain ) >> FP_SHIFT;
				mixBuffer[ mixIdx++ ] += ( y * rightGain ) >> FP_SHIFT;
				sampleFrac += step;
				sampleIdx += sampleFrac >> FP_SHIFT;
				sampleFrac &= FP_MASK;
			}
		}
	}

	public int normalizeSampleIdx( int sampleIdx ) {
		int loopOffset = sampleIdx - loopStart;
		if( loopOffset > 0 ) {
			sampleIdx = loopStart;
			if( loopLength > 1 ) {
				sampleIdx += loopOffset % loopLength;
			}
		}
		return sampleIdx;
	}

	public int load( byte[] module, int offset, int sample ) {
		return 0;
	}
	
	public int save( byte[] module, int offset, int sample ) {
		int sampleLength = ( loopStart & 2 ) + ( loopLength & -2 );
		System.arraycopy( sampleData, 0, module, offset, sampleLength );
		return offset + sampleLength;
	}
}
