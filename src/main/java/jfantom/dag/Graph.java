package jfantom.dag;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jfantom.util.Appender;
import jfantom.util.Common;

/**
 * Graph is imaginary Operachain
 */
public class Graph {
	public Vertex Tip;
	public Map<String,Vertex> ChkVertex;
	public Map<String,Vertex> ChkClotho;
	public Map<String,Vertex> ClothoList;
	public Map<String,Vertex> AtroposList;
	public Map<String,Map<String,Long>> TimeTable;
	public List<Vertex> SortList;

	public Graph(Vertex tip, Map<String, Vertex> chkVertex, Map<String, Vertex> chkClotho,
			Map<String, Vertex> clothoList, Map<String, Vertex> atroposList, Map<String, Map<String, Long>> timeTable,
			Vertex[] verticies) {
		super();
		Tip = tip;
		ChkVertex = chkVertex;
		ChkClotho = chkClotho;
		ClothoList = clothoList;
		AtroposList = atroposList;
		TimeTable = timeTable;
		SortList = Arrays.asList(verticies);
	}

	/**
	 * ClothoChecking checks whether ancestor of the vertex is colotho
	 * @param v
	 */
	public void ClothoChecking(Vertex v) {
		// Clotho check
		Map<String,Map<String,Boolean>> ccList = new HashMap<String,Map<String,Boolean>>();

		for(String key : v.RootTable.keySet()) {
			int val = v.RootTable.get(key);
			Vertex prevRoot = ChkClotho.get(val + "_" + key);
			if (prevRoot == null) {
				continue;
			}

			for (String rkey : prevRoot.RootTable.keySet()) {
				int rval = prevRoot.RootTable.get(rkey);
				Vertex prevPrevRoot = ChkClotho.get(rval + "_" + rkey);
				if (prevPrevRoot == null) {
					continue;
				}

				for (String rrkey : prevPrevRoot.RootTable.keySet()) {
					int rrval = prevPrevRoot.RootTable.get(rrkey);
					boolean exists = ChkClotho.containsKey(rrval+"_"+rrkey);
					if (!exists) {
						continue;
					}
					if (ccList.get(rrval+"_"+rrkey) == null) {
						ccList.put(rrval+"_"+rrkey, new HashMap<String,Boolean>());
					}

					boolean exists2 = ccList.get(rrval+"_"+rrkey).get(rval+"_"+rkey);
					if (!exists2) {
						ccList.get(rrval+"_"+rrkey).put(rval +"_"+rkey, true);
					}
				}
			}
		}

		for (String key : ccList.keySet()) {
			Map<String,Boolean> val = ccList.get(key);
			if (val.size() >= Common.subMajor) {
				Vertex prevRoot = ChkClotho.get(key);
				ClothoList.put(key, prevRoot);
				prevRoot.Clotho = true;
				if (TimeTable.get(v.Frame+"_"+v.Signature) == null) {
					TimeTable.put(v.Frame+"_"+v.Signature, new HashMap<String,Long>());
				}
				TimeTable.get(v.Frame+"_"+v.Signature).put(prevRoot.Frame+"_"+prevRoot.Signature,  v.Timestamp);
				System.out.printf("%s is assigned as Clotho by %s\n", key, v.Frame+"_"+v.Signature);
			}
		}
	}

	// AtroposTimeSelection selects time from set of previous vertex
	public void AtroposTimeSelection(Vertex v) {
		Map<String, Map<Long, Integer>> countMap = new HashMap<String,Map<Long,Integer>>();

		for (String prevKey : v.RootTable.keySet()) {
			int prevVal = v.RootTable.get(prevKey);
			String prevSig = prevVal + "_" + prevKey;

			Map<String, Long> ttPrevSigMap = TimeTable.get(prevSig);
			for (String key : ttPrevSigMap.keySet())  {
				long val = ttPrevSigMap.get(key);

				Map<Long, Integer> valMap = countMap.get(key);
				if (valMap == null) {
					valMap = new HashMap<Long,Integer>();
					countMap.put(key, valMap);
				}
				//System.out.println("key")
				Integer cval = valMap.get(val);
				if (cval != null) {
					valMap.put(val, cval + 1);
				} else {
					valMap.put(val, 1);
				}
			}
		}

		for (String key : countMap.keySet()) {
			Map<Long, Integer> val = countMap.get(key);
			int maxVal = 0;
			long maxInd = -1;

			Vertex clotho = ClothoList.get(key);
			if ((v.Frame-clotho.Frame)%4 == 0) {
				for (long time : val.keySet()) {
					int count = val.get(time);
					if (maxVal == 0) {
						maxVal = count;
						maxInd = time;
					} else if (time < maxInd) {
						maxInd = time;
					}
				}

				TimeTable.get(v.Frame+"_"+v.Signature).put(key, maxInd);
			} else {
				for (long time : val.keySet()) {
					int count = val.get(time);
					if (count > maxVal) {
						maxVal = count;
						maxInd = time;
					} else if (count == maxVal && time < maxInd) {
						maxInd = time;
					}
				}

				if (maxVal >= Common.supraMajor) {
					System.out.println("atropos" + " " + clotho.Signature + " " + clotho.Frame + " " + maxInd);
					clotho.Atropos = true;
					clotho.AtroposTime = maxInd;
					AssignAtroposTime(clotho);
				} else {
					TimeTable.get(v.Frame+"_"+v.Signature).put(key, maxInd);
				}
			}
		}
	}

	// AssignAtroposTime is
	public void AssignAtroposTime(Vertex atropos) {
		Vertex[] batchList = new Vertex[]{};
		Vertex[] sortList = new Vertex[]{};
		long aTime = atropos.AtroposTime;

		batchList = Appender.append(batchList, atropos);
		while (true) {
			if (batchList.length == 0) {
				break;
			}

			Vertex currentVertex = batchList[0];
			batchList = Appender.sliceFromToEnd(batchList, 1);
			//System.out.println(1, sortList)

			sortList = Appender.append(new Vertex[] {currentVertex}, sortList);
			//System.out.println(2, sortList)
			boolean chk = false;
			if (currentVertex.AtroposTime == 0 || aTime < currentVertex.AtroposTime) {
				currentVertex.AtroposTime = aTime;
				chk = true;
			}

			if (chk) {
				if (currentVertex.PrevSelf != null) {
					batchList = Appender.append(batchList, currentVertex.PrevSelf);
				}
				if (currentVertex.PrevOther != null) {
					batchList = Appender.append(batchList, currentVertex.PrevOther);
				}
			}
		}

		//System.out.println(sortList)
		//Sort vertex
		while (true) {
			if (sortList.length == 0) {
				break;
			}

			Vertex currentVertex = sortList[0];
			sortList = Appender.sliceFromToEnd(sortList, 1);

			int index = SortList.size() - 1;

			while (true) {
				if (index < 0) {
					break;
				}
				Vertex compVertex = SortList.get(index);
				if (compVertex.AtroposTime < currentVertex.AtroposTime) {
					break;
				} else if (compVertex.AtroposTime == currentVertex.AtroposTime) {
					if (compVertex.Timestamp < currentVertex.Timestamp) {
						break;
					} else if (compVertex.Timestamp == currentVertex.Timestamp) {
						boolean chk = false;
						for (int idn = 0; idn < currentVertex.Hash.length; ++idn) {
							byte val = currentVertex.Hash[idn];
							if (val > compVertex.Hash[idn]) {
								chk = true;
								break;
							} else if (val < compVertex.Hash[idn]) {
								chk = false;
								break;
							}
						}

						if (chk) {
							break;
						}
					}
				}

				index--;
			}
			Insert(index+1, currentVertex);
		}
	}

	// Insert item into list
	public void Insert(int i, Vertex v) {
		SortList.add(i, v);
	}

	// Merge is union between parent flagtable
	public Map<String,Integer> Merge(Map<String,Integer>  sv, Map<String,Integer>  ov, int fNum) {
		Map<String,Integer> ret = new HashMap<String,Integer>();
		for (String sKey : sv.keySet()) {
			int sVal = sv.get(sKey);
			if (sVal == fNum) {
				ret.put(sKey, sVal);
			}
		}

		for (String oKey : ov.keySet()) {
			int oVal = ov.get(oKey);
			boolean exists = ret.containsKey(oKey);
			if (!exists) {
				if (oVal == fNum) {
					ret.put(oKey, oVal);
				}
			}
		}

		return ret;
	}

	// Copy copies flagtable into roottalbe
	public Map<String,Integer> Copy(Map<String,Integer> c) {
		return new HashMap<String,Integer>(c);
	}

	// Max selects maximum value between parent frame numbers
	public int Max(int sf, int of) {
		if (sf > of) {
			return sf;
		}
		return of;
	}
}

