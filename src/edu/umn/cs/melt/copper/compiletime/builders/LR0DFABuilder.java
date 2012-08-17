package edu.umn.cs.melt.copper.compiletime.builders;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Hashtable;

import edu.umn.cs.melt.copper.compiletime.lrdfa.LR0DFA;
import edu.umn.cs.melt.copper.compiletime.lrdfa.LR0ItemSet;
import edu.umn.cs.melt.copper.compiletime.spec.numeric.ParserSpec;

/**
 * Builds an LR(0) DFA (without any lookahead sets on items). 
 * @author August Schwerdfeger &lt;<a href="mailto:schwerdf@cs.umn.edu">schwerdf@cs.umn.edu</a>&gt;
 *
 */
public class LR0DFABuilder
{
	private ParserSpec spec;
	private int transitionArraySize;
	private Hashtable<LR0ItemSet,Integer> memoizedClosures = new Hashtable<LR0ItemSet,Integer>();
	
	private ArrayList<LR0ItemSet> itemSets = new ArrayList<LR0ItemSet>();
	private ArrayList<BitSet> transitionLabels = new ArrayList<BitSet>();
	private ArrayList<int[]> transitions = new ArrayList<int[]>();
	private ArrayList<BitSet[]> gotoItems = new ArrayList<BitSet[]>();
	
	private BitSet closureProductions = new BitSet();
	private BitSet closure_newItems0 = new BitSet();
	private BitSet closure_newItems1 = new BitSet();
	
	private LR0DFABuilder(ParserSpec spec)
	{
		this.spec = spec;
		this.transitionArraySize = Math.max(spec.terminals.length(),spec.nonterminals.length());
	}

	public static LR0DFA build(ParserSpec spec)
	{
		return new LR0DFABuilder(spec).buildDFA();
	}
	
	private LR0DFA buildDFA()
	{
		itemSets.add(new LR0ItemSet(new int[0]));
		transitionLabels.add(new BitSet());
		transitions.add(new int[transitionArraySize]);
		gotoItems.add(new BitSet[transitionArraySize]);
		
		// Initialize E to empty.
		BitSet fringe1 = new BitSet();
		BitSet fringe2 = new BitSet();
		// Initialize T to {Closure([STARTPRIME ::= (*) startSym EOF,  t]}.
		int[] primeSeedItems = { spec.getStartProduction(),0 };
		int J = closure(new LR0ItemSet(primeSeedItems));
		fringe1.set(J);
		// Repeat until E and T do not change:
		boolean setsChanged = true;
		while(setsChanged)
		{
			setsChanged = false;
			// For each state I in T:
			for(int i = fringe1.nextSetBit(0);i >= 0;i = fringe1.nextSetBit(i+1))
			{
				// For each item [A ::= a (*) X b,  w] in I:
				calculateGotos(i,fringe2);
			}
			if(!fringe2.isEmpty())
			{
				fringe1.clear();
				fringe1.or(fringe2);
				fringe2.clear();
				setsChanged = true;
			}
		}
		
		LR0ItemSet[] statesA = new LR0ItemSet[itemSets.size()];
		itemSets.toArray(statesA);
		BitSet[] transitionLabelsA = new BitSet[transitionLabels.size()];
		transitionLabels.toArray(transitionLabelsA);
		int[][] transitionsA = new int[transitions.size()][transitionArraySize];
		transitions.toArray(transitionsA);
		BitSet[][] gotoItemsA = new BitSet[gotoItems.size()][transitionArraySize];
		gotoItems.toArray(gotoItemsA);
		
		return new LR0DFA(statesA,transitionLabelsA,transitionsA,gotoItemsA);
	}
	
	public int closure(LR0ItemSet seed)
	{
		if(memoizedClosures.containsKey(seed)) return memoizedClosures.get(seed);
		closureProductions.clear();
		
		closure_newItems0.clear();
		closure_newItems1.clear();
		
		// One can tell the difference between a seed item
		// and a closure-generated item by the bullet position:
		// only closure-generated items and ^ ::= (*) S $, which
		// is not generated by any closure, have the bullet at
		// the beginning (position = 0).

		// For each seed item:
		for(int i = 0;i < seed.size();i++)
		{
			// If the item's bullet is not at the end
			if(seed.getPosition(i) < spec.pr.getRHSLength(seed.getProduction(i)))
			{
				int sym = spec.pr.getRHSSym(seed.getProduction(i),seed.getPosition(i));
				// and is situated to the left of a nonterminal
				// (i.e., the item is of the form [A ::= a (*) X b]):
				if(spec.nonterminals.get(sym))
				{
					// Mark all productions [X ::= g] for addition to the closure.
					closure_newItems1.or(spec.nt.getProductions(sym));
				}
			}
		}
		
		closureProductions.or(closure_newItems1);
		
		// Repeat until the item set I does not change:
		boolean iChanged = true;
		while(iChanged)
		{
			iChanged = false;
			closure_newItems0.clear();
			closure_newItems0.or(closure_newItems1);
			closure_newItems1.clear();
			// For any item [A ::= a (*) X b] in I
			// (but as mentioned above, the bullet will invariably be at the
			//  beginning, so a is always the empty sequence):
			for(int p = closure_newItems0.nextSetBit(0);p >= 0;p = closure_newItems0.nextSetBit(p+1))
			{
				// Check that the item's bullet is not at the end (with the bullet at the
				// beginning, this means checking that the production does not have an
				// empty RHS) and that the first RHS symbol is a nonterminal X.
				if(spec.pr.getRHSLength(p) > 0 && spec.nonterminals.get(spec.pr.getRHSSym(p,0)))
				{
					// If so, I := union(I,[X ::= (*) g]).
					closure_newItems1.or(spec.nt.getProductions(spec.pr.getRHSSym(p,0)));
				}
				iChanged |= ParserSpec.union(closureProductions,closure_newItems1);
			}
		}
		
		int[] newItems = new int[2 * (seed.size() + closureProductions.cardinality())];
		seed.copyItems(newItems);
		for(int i = seed.size(),p = closureProductions.nextSetBit(0);p >= 0;p = closureProductions.nextSetBit(p+1))
		{
			newItems[2*(i++)] = p;
		}
		LR0ItemSet newClosure = new LR0ItemSet(newItems);
		itemSets.add(newClosure);
		transitionLabels.add(new BitSet());
		transitions.add(new int[transitionArraySize]);
		gotoItems.add(new BitSet[transitionArraySize]);
		memoizedClosures.put(seed,itemSets.size() - 1);
		memoizedClosures.put(newClosure,itemSets.size() - 1);
		return itemSets.size() - 1;
	}
	
	public void calculateGotos(int state,BitSet gotos)
	{
		LR0ItemSet I = itemSets.get(state);
		
		// For any item [A ::= a (*) X b] in I:
		for(int i = 0;i < I.size();i++)
		{
			if(I.getPosition(i) < spec.pr.getRHSLength(I.getProduction(i)))
			{
				int X = spec.pr.getRHSSym(I.getProduction(i),I.getPosition(i));
				if(X == spec.getEOFTerminal()) continue;
				if(!transitionLabels.get(state).get(X)) gotoItems.get(state)[X] = new BitSet();
				// Let delta(I,X) = J;
				transitionLabels.get(state).set(X);
				// designate [A ::= a X (*) b] as a seed item for state J.
				gotoItems.get(state)[X].set(i);
			}
		}
		
		// For each transition X:
		for(int X = transitionLabels.get(state).nextSetBit(0);X >= 0;X = transitionLabels.get(state).nextSetBit(X+1))
		{
			// Build the set of seed items for state J = delta(I,X) based on the designations
			// made in the previous loop.
			int[] jItems = new int[2*gotoItems.get(state)[X].cardinality()];
			for(int i = 0,item = gotoItems.get(state)[X].nextSetBit(0);item >= 0;i++,item = gotoItems.get(state)[X].nextSetBit(item+1))
			{
				jItems[2*i] = I.getProduction(item);
				jItems[2*i+1] = I.getPosition(item) + 1;
			}
			// Get the closure for the seed item set -- whether a new state or one
			// already existing.
			int J = closure(new LR0ItemSet(jItems));
			// Put the I-to-J transition in the delta table.
			transitions.get(state)[X] = J;
			// If a new state was created, add it to the set of new itemSets next to be processed.
			if(J == itemSets.size() - 1) gotos.set(J);
		}
	}
}